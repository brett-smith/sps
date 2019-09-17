package com.sshtools.forker.plugin.maven.plugin;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

public abstract class AbstractForkerPluginMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	protected MavenProject project;

	@Parameter(defaultValue = "${session}", readonly = true, required = true)
	protected MavenSession session;

	@Parameter(property = "reactorProjects", required = true, readonly = true)
	protected List<MavenProject> reactorProjects;

	/**
	 * The Sign Config.
	 */
	@Parameter
	protected SignConfig sign;

	/**
	 * Sign tool.
	 */
	@Component
	protected SignTool signTool;

	/**
	 * Enable verbose output.
	 */
	@Parameter(defaultValue = "false")
	protected boolean verbose;

	@Parameter
	protected FeatureConfig ui = new FeatureConfig();

	/**
	 * The path where the resources are placed within the structure.
	 */
	@Parameter(defaultValue = "")
	protected String resourceSetsPath;

	/**
	 * Compile class-path elements used to search for the keystore (if kestore
	 * location was prefixed by {@code classpath:}).
	 * 
	 * @since 1.0-beta-4
	 */
	@Parameter(defaultValue = "${project.compileClasspathElements}", required = true, readonly = true)
	protected List<?> compileClassPath;

	protected abstract File getWorkDirectory();

	protected void initSign() throws MojoExecutionException {
		if (sign != null) {
			try {
				ClassLoader loader = getCompileClassLoader();
				sign.init(getWorkDirectory(), getLog().isDebugEnabled(), signTool, loader);
			} catch (MalformedURLException e) {
				throw new MojoExecutionException("Could not create classloader", e);
			}
		}
	}

	private ClassLoader getCompileClassLoader() throws MalformedURLException {
		URL[] urls = new URL[compileClassPath.size()];
		for (int i = 0; i < urls.length; i++) {
			String spec = compileClassPath.get(i).toString();
			URL url = new File(spec).toURI().toURL();
			urls[i] = url;
		}
		return new URLClassLoader(urls);
	}

	/**
	 * Computes the path for a file relative to a given base, or fails if the only
	 * shared directory is the root and the absolute form is better.
	 * 
	 * @param base File that is the base for the result
	 * @param name File to be "relativized"
	 * @return the relative name
	 * @throws IOException if files have no common sub-directories, i.e. at best
	 *                     share the root prefix "/" or "C:\"
	 * 
	 *                     http://stackoverflow.com/questions/204784/how-to-construct-a-
	 *                     relative-path-in-java-from-two-absolute-paths-or-urls
	 */

	public static String getRelativePath(File base, File name) throws IOException {
		File parent = base.getParentFile();

		if (parent == null) {
			throw new IOException("No common directory");
		}

		String bpath = base.getCanonicalPath();
		String fpath = name.getCanonicalPath();

		if (fpath.startsWith(bpath)) {
			return fpath.substring(bpath.length() + 1);
		} else {
			return (".." + File.separator + getRelativePath(parent, name));
		}
	}

	/**
	 * Log as info when verbose or info is enabled, as debug otherwise.
	 * 
	 * @param msg the message to display
	 */
	protected void verboseLog(String msg) {
		if (verbose) {
			getLog().info(msg);
		} else {
			getLog().debug(msg);
		}
	}

}
