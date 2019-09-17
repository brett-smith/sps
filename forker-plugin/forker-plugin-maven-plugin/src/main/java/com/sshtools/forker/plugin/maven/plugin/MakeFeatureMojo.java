package com.sshtools.forker.plugin.maven.plugin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.AndArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ExcludesArtifactFilter;
import org.apache.maven.artifact.resolver.filter.IncludesArtifactFilter;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.sshtools.forker.plugin.AnnotationFinder;
import com.sshtools.forker.plugin.Hashing;
import com.sshtools.forker.plugin.api.Plugin;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;

@Mojo(inheritByDefault = false, name = "feature", aggregator = true, requiresDependencyResolution = ResolutionScope.RUNTIME)
public class MakeFeatureMojo extends AbstractForkerPluginMojo {

	/**
	 * The directory in which files will be stored prior to processing.
	 */
	@Parameter(defaultValue = "${project.build.directory}/feature", required = true)
	private File workDirectory;

	/**
	 * [optional] transitive dependencies filter - if omitted, the plugin will
	 * include all transitive dependencies. Provided and test scope dependencies are
	 * always excluded.
	 */
	@Parameter
	private Dependencies dependencies;

	/**
	 * Set to true to exclude all transitive dependencies.
	 * 
	 * @parameter
	 */
	@Parameter
	private boolean excludeTransitive;

	public static class ResourceSet {

		@Parameter
		private String path;

		@Parameter
		private String[] includes;

		@Parameter
		private String[] excludes;
	}

	/**
	 * Represents the configuration element that specifies which of the current
	 * project's dependencies will be included or excluded from the resources
	 * element in the generated JNLP file.
	 */
	public static class Dependencies {

		private List<String> includes;

		private List<String> excludes;

		public List<String> getIncludes() {
			return includes;
		}

		public void setIncludes(List<String> includes) {
			this.includes = includes;
		}

		public List<String> getExcludes() {
			return excludes;
		}

		public void setExcludes(List<String> excludes) {
			this.excludes = excludes;
		}
	}

	//
	private Set<Artifact> packagedArchives = new LinkedHashSet<Artifact>();
	private final List<String> modifiedArchives = new ArrayList<String>();

	public void execute() throws MojoExecutionException {

		getLog().debug("using work directory " + workDirectory);

		Util.makeDirectoryIfNecessary(workDirectory);

		try {
			initSign();

			AndArtifactFilter filter = new AndArtifactFilter();
			// filter.add( new ScopeArtifactFilter( dependencySet.getScope() ) );

			if (dependencies != null && dependencies.getIncludes() != null && !dependencies.getIncludes().isEmpty()) {
				filter.add(new IncludesArtifactFilter(dependencies.getIncludes()));
			}
			if (dependencies != null && dependencies.getExcludes() != null && !dependencies.getExcludes().isEmpty()) {
				filter.add(new ExcludesArtifactFilter(dependencies.getExcludes()));
			}

			/* Only execute in the reactor module */
			processProjects(filter);
			makeFeatureFile();
			makeFeaturesFile();
		} catch (MojoExecutionException e) {
			throw e;
		} catch (Exception e) {
			throw new MojoExecutionException("Failure to run the plugin: ", e);
		}
	}

	protected void makeFeaturesFile() throws FileNotFoundException {
		PrintWriter writer = new PrintWriter(new File(workDirectory, "features.txt"));
		try {
			writer.println(
					project.getGroupId() + "@" + project.getArtifactId() + "@" + project.getVersion() + "@FEATURE.xml");
		} finally {
			writer.close();
		}
	}

	protected void makeFeatureFile() throws IOException, ParserConfigurationException, TransformerException {

		String id = project.getGroupId() + "@" + project.getArtifactId() + "@" + project.getVersion();

		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

		// The hash for the feature is a hash of all the hashes of the packaged
		// artifacts
		List<String> hashes = new LinkedList<String>();
		for (Artifact s : packagedArchives) {
			String sha1 = Hashing.sha1(s.getFile());
			hashes.add(sha1);
		}
		String featureHash = Hashing.hash(String.join(":", hashes));

		// Feature
		Document doc = docBuilder.newDocument();
		Element rootElement = doc.createElement("feature");
		rootElement.setAttribute("id", id);
		rootElement.setAttribute("hash", featureHash);
		doc.appendChild(rootElement);

		// Archives
		Element archives = doc.createElement("archives");
		rootElement.appendChild(archives);

		// Create a classloader we can use to load annotations. We put everything
		// discovered
		// on the classpath
		Set<URL> urls = new HashSet<>();
		for (Artifact s : packagedArchives) {
			if (isForkerLibrary(s))
				getLog().debug("Skipping " + s.getFile() + " from classpath because it is forker plugin library");
			else
				urls.add(s.getFile().toURI().toURL());
		}
		URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]), getClass().getClassLoader());
		getLog().debug("Scan classpath: " + urls.toString());

		Iterator<String> hashIt = hashes.iterator();
		for (Artifact s : packagedArchives) {
			Element archive = doc.createElement("archive");
			archive.setAttribute("size", String.valueOf(s.getFile().length()));
			String sha1 = hashIt.next();
			verboseLog("Scanning dependency  " + s.getArtifactId() + " (" + s.getFile() + "): " + sha1);
			archive.setAttribute("hash", sha1);
			archive.setAttribute("id", s.getGroupId() + "@" + s.getArtifactId() + "@" + (s.isOptional() ? "~" : "") + s.getVersion());
			archives.appendChild(archive);
			try {
				new AnnotationFinder(loader, s.getFile().toURI().toURL()) {

					@Override
					protected void onPlugin(Class<?> clazz, Plugin plugin) throws IOException {
						verboseLog("Found plugin " + clazz);
						Element pluginEl = doc.createElement("plugin");
						pluginEl.setAttribute("class", clazz.getName());
						pluginEl.setAttribute("staticLoad", String.valueOf(plugin.staticLoad()));
						pluginEl.setAttribute("start", plugin.start().name());
						if (StringUtils.isNotBlank(plugin.name()))
							pluginEl.setAttribute("name", plugin.name());
						if (StringUtils.isNotBlank(plugin.description())) {
							Element descEl = doc.createElement("description");
							descEl.appendChild(doc.createCDATASection(plugin.description()));
							pluginEl.appendChild(descEl);
						}
						for (String dep : plugin.dependencies()) {
							Element depEl = doc.createElement("dependency");
							depEl.appendChild(doc.createTextNode(dep));
							pluginEl.appendChild(depEl);
						}
						archive.appendChild(pluginEl);
					}
				}.find(new PluginProgressMonitor() {
					@Override
					public void message(MessageType type, String message) {
						switch (type) {
						case INFO:
							getLog().info(message);
							break;
						case DEBUG:
							getLog().debug(message);
							break;
						case ERROR:
							getLog().error(message);
							break;
						case WARNING:
							getLog().warn(message);
							break;
						}
					}
				});
			} catch (ClassNotFoundException cnfe) {
				getLog().error("Failed to scan for plugins.", cnfe);
			}
		}

		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer transformer = transformerFactory.newTransformer();
		DOMSource source = new DOMSource(doc);
		StreamResult result = new StreamResult(new File(workDirectory, id + "@FEATURE.xml"));
		transformer.transform(source, result);
	}

	protected boolean isForkerLibrary(Artifact s) {
		return s.getGroupId().equals("com.sshtools")
				&& (s.getArtifactId().equals("forker-plugin-api") || s.getArtifactId().equals("forker-plugin-system"));
	}

	/**
	 * Detects improper includes/excludes configuration.
	 * 
	 * @throws MojoExecutionException if at least one of the specified includes or
	 *                                excludes matches no artifact, false otherwise
	 */
	void checkDependencies() throws MojoExecutionException {
		if (dependencies == null) {
			return;
		}

		boolean failed = false;

		Collection<Artifact> artifacts = project.getArtifacts();

		getLog().debug("artifacts: " + artifacts.size());

		if (dependencies.getIncludes() != null && !dependencies.getIncludes().isEmpty()) {
			failed = checkDependencies(dependencies.getIncludes(), artifacts);
		}
		if (dependencies.getExcludes() != null && !dependencies.getExcludes().isEmpty()) {
			failed = checkDependencies(dependencies.getExcludes(), artifacts) || failed;
		}

		if (failed) {
			throw new MojoExecutionException(
					"At least one specified dependency is incorrect. Review your project configuration.");
		}
	}

	private void processProjects(AndArtifactFilter filter) throws MojoExecutionException {

		processProject(project, filter);
		for (MavenProject reactorProject : reactorProjects) {
			if (reactorProject != project)
				processProject(reactorProject, filter);
		}
	}

	/**
	 * Iterate through all the top level and transitive dependencies declared in the
	 * project and collect all the runtime scope dependencies for inclusion in the
	 * .zip and signing.
	 * 
	 * @throws MojoExecutionException if could not process dependencies
	 */
	public void processProject(MavenProject projectToProject, AndArtifactFilter filter) throws MojoExecutionException {

		verboseLog("Process project " + projectToProject.getId());

		processDependency(projectToProject, projectToProject.getArtifact());

		for (Artifact artifact : projectToProject.getArtifacts()) {
			processDependency(projectToProject, artifact);
		}
	}

	private void processDependency(MavenProject projectToProcess, Artifact artifact) throws MojoExecutionException {
		if (excludeTransitive) {
			boolean trans = true;
			for (MavenProject reactorProject : reactorProjects) {
				if (reactorProject.getArtifact().equals(artifact)) {
					trans = false;
				}
			}
			if (trans) {
				trans = false;
				for (Dependency dep : projectToProcess.getDependencies()) {
					if (Objects.equals(dep.getArtifactId(), artifact.getArtifactId())
							&& Objects.equals(dep.getGroupId(), artifact.getGroupId())
							&& Objects.equals(dep.getVersion(), artifact.getVersion())
							&& Objects.equals(dep.getType(), artifact.getType())
							&& Objects.equals(dep.getClassifier(), artifact.getClassifier())) {

						trans = true;
						break;
					}
				}
			}
			if (trans) {
				verboseLog("  Skipping dependency  " + artifact.getId() + " because it is transitive");
				return;
			}
		}

		if (artifact.getDependencyTrail() == null
				&& !(artifact.getType().equals("jar") && projectToProcess.getArtifact().equals(artifact))) {
			verboseLog("  Skipping dependency  " + artifact.getId() + ", it is a framework library");
			return;
		} else
			verboseLog("  Process dependency  " + artifact.getId() + (artifact.getDependencyTrail() == null ? "NODEP"
					: ("[ " + String.join("/", artifact.getDependencyTrail()) + "]")));

		if (isForkerLibrary(artifact)) {
			verboseLog("  Skipping forker-plugin-api artifact");
			return;
		}

		if (!Artifact.SCOPE_SYSTEM.equals(artifact.getScope()) && !Artifact.SCOPE_PROVIDED.equals(artifact.getScope())
				&& !Artifact.SCOPE_TEST.equals(artifact.getScope())) {
			String type = artifact.getType();
			if ("jar".equals(type) || "ejb-client".equals(type)) {

				// FIXME when signed, we should update the manifest.
				// see
				// http://www.mail-archive.com/turbine-maven-dev@jakarta.apache.org/msg08081.html
				// and maven1:
				// maven-plugins/jnlp/src/main/org/apache/maven/jnlp/UpdateManifest.java
				// or shouldn't we? See MOJO-7 comment end of October.
				final File toCopy = artifact.getFile();

				if (toCopy == null) {
					getLog().error("artifact with no file: " + artifact);
					getLog().error("artifact download url: " + artifact.getDownloadUrl());
					getLog().error("artifact repository: " + artifact.getRepository());
					getLog().error("artifact repository: " + artifact.getVersion());
					return;
				}
				verboseLog("  File " + toCopy);

				String name = getDependencyFileBasename(artifact, artifact.getType());

				boolean copied = copyFileAsUnprocessedToDirectoryIfNecessary(toCopy, workDirectory, name);

				/* Look for a POM at the same place */
				File pomFile = new File(toCopy.getParentFile(), FilenameUtils.getBaseName(toCopy.getName()) + ".pom");
				if (pomFile.exists()) {
					copyFileAsUnprocessedToDirectoryIfNecessary(pomFile, workDirectory,
							getDependencyFileBasename(artifact, "pom"));
				} else {
					for (MavenProject p : reactorProjects) {
						if (p.getArtifact().equals(artifact)) {
							copyFileAsUnprocessedToDirectoryIfNecessary(p.getFile(), workDirectory,
									getDependencyFileBasename(artifact, "pom"));
							break;
						}
					}
				}

				if (copied) {
					int lastIndexOf = name.lastIndexOf('.');
					if (lastIndexOf == -1) {
						modifiedArchives.add(name);
					} else {
						modifiedArchives.add(name.substring(0, lastIndexOf));
					}
				}

				packagedArchives.add(artifact);

			} else
			// FIXME how do we deal with native libs?
			// we should probably identify them and package inside jars that we
			// timestamp like the native lib
			// to avoid repackaging every time. What are the types of the native
			// libs?
			{
				verboseLog("Skipping artifact of type " + type + " for " + workDirectory.getName());
			}
			// END COPY
		} else {
			verboseLog("Skipping artifact of scope " + artifact.getScope() + " for " + workDirectory.getName());
		}
	}

	public String getDependencyFileBasename(Artifact artifact, String type) {
		return artifact.getGroupId() + "@" + artifact.getArtifactId() + "@" + artifact.getVersion() + "." + type;
	}

	protected File getWorkDirectory() {
		return workDirectory;
	}

	protected boolean copyFileAsUnprocessedToDirectoryIfNecessary(File sourceFile, File targetDirectory,
			String targetFilename) throws MojoExecutionException {

		if (sourceFile == null) {
			throw new IllegalArgumentException("sourceFile is null");
		}

		if (targetFilename == null) {
			targetFilename = sourceFile.getName();
		}

		File signedTargetFile = new File(targetDirectory, targetFilename);

		boolean shouldCopy = !signedTargetFile.exists()
				|| (signedTargetFile.lastModified() < sourceFile.lastModified());

		if (shouldCopy) {
			Util.copyFile(sourceFile, signedTargetFile);

		} else {
			getLog().debug(
					"Source file hasn't changed. Do not reprocess " + signedTargetFile + " with " + sourceFile + ".");
		}

		return shouldCopy;
	}

	/**
	 * @param patterns  list of patterns to test over artifacts
	 * @param artifacts collection of artifacts to check
	 * @return true if at least one of the pattern in the list matches no artifact,
	 *         false otherwise
	 */
	private boolean checkDependencies(List<String> patterns, Collection<Artifact> artifacts) {
		if (dependencies == null) {
			return false;
		}

		boolean failed = false;
		for (String pattern : patterns) {
			failed = ensurePatternMatchesAtLeastOneArtifact(pattern, artifacts) || failed;
		}
		return failed;
	}

	/**
	 * @param pattern   pattern to test over artifacts
	 * @param artifacts collection of artifacts to check
	 * @return true if filter matches no artifact, false otherwise *
	 */
	private boolean ensurePatternMatchesAtLeastOneArtifact(String pattern, Collection<Artifact> artifacts) {
		List<String> onePatternList = new ArrayList<String>();
		onePatternList.add(pattern);
		ArtifactFilter filter = new IncludesArtifactFilter(onePatternList);

		boolean noMatch = true;
		for (Artifact artifact : artifacts) {
			getLog().debug("checking pattern: " + pattern + " against " + artifact);

			if (filter.include(artifact)) {
				noMatch = false;
				break;
			}
		}
		if (noMatch) {
			getLog().error("pattern: " + pattern + " doesn't match any artifact.");
		}
		return noMatch;
	}

}
