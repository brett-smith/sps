package com.sshtools.forker.plugin.osgi;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.commons.lang3.StringUtils;

import com.sshtools.forker.plugin.AbstractArchiveWithDependencies;
import com.sshtools.forker.plugin.HaltResolutionException;
import com.sshtools.forker.plugin.JarArchive;
import com.sshtools.forker.plugin.ResolutionRetryException;
import com.sshtools.forker.plugin.api.PluginComponentId;
import com.sshtools.forker.plugin.api.PluginDependencyTree;
import com.sshtools.forker.plugin.api.PluginManager;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;
import com.sshtools.forker.plugin.api.ResolutionState;

public class BundleJarArchive extends AbstractArchiveWithDependencies {

	private boolean manifestResolved;

	public BundleJarArchive(PluginManager manager, URL archive) {
		super(manager, archive);
		if (!readManifest(false))
			throw new IllegalArgumentException(String.format("%s is not a Maven Jar.", getArchive()));
	}

	public BundleJarArchive(PluginManager manager, PluginComponentId spec) {
		super(manager, null, spec);
	}

	@Override
	protected ResolutionState calcState() {
		ResolutionState base = calcBaseState();
		if (base.isResolved()) {
			base = calcArchiveState();
			if (base.isResolved()) {
				base = calcPOMState();
				if (base.isResolved()) {
					base = calcPluginsState();
					if (base.isResolved())
						base = calcChildrenState();
				}
			}
		}
		return base;
	}

	@Override
	protected void doResolve(PluginProgressMonitor monitor) throws IOException {
		if (!calcArchiveState().isResolved())
			resolveArchive(monitor);

		if (!calcPOMState().isResolved()) {
			try {
				resolveManifest(monitor);
			} catch (IllegalArgumentException iae) {
				/*
				 * A Bundle archive declared a dependency that exists but does not have any
				 * Maven meta-data in it. So we morph into a plain jar
				 */
				JarArchive jararc = new JarArchive(getManager(), getArchive());
				PluginDependencyTree depTree = getManager().getResolutionContext().getDependencyTree();
				depTree.remove(this);
				depTree.add(jararc, monitor);
			}
			return;
		}

		resolvePluginsState(monitor);

		if (!calcNodeState().isComplete())
			resolveNode(monitor);

		resolveChildren(monitor);

	}

	protected final ResolutionState calcPOMState() {
		return manifestResolved ? ResolutionState.RESOLVED : ResolutionState.UNRESOLVED;
	}

	protected void resolveManifest(PluginProgressMonitor monitor) throws IOException {
		if (getArchive() == null) {
			if (!getComponentId().isOptional() && getManager().getResolutionContext().failOnError())
				throw new ResolutionRetryException(
						String.format("Archive for %s not known, cannot resolve.", getComponentId()), this);
		} else if (!readManifest(true)) {
			throw new HaltResolutionException(String.format("%s is not a Bundle Jar.", getArchive()), this);
		}
		manifestResolved = true;
		getManager().getResolutionContext().getDependencyTree().add(this, monitor);
	}

	protected boolean readManifest(boolean deps) {
		try {

			File file = new File(getArchive().toURI().toURL().getPath());

			try (JarFile jf = new JarFile(file)) {

				JarEntry manifest = jf.getJarEntry("META-INF/MANIFEST.MF");
				if (manifest != null) {
					Manifest mf = new Manifest(jf.getInputStream(manifest));
					Attributes attr = mf.getMainAttributes();
					boolean isBundle = false;

					// Version
					String value = attr.getValue("Bundle-Version");
					if (StringUtils.isNotBlank(value)) {
						setComponent(getComponentId().withVersion(value));
						isBundle = true;
					}

					// Group?
					value = attr.getValue("Bundle-SymbolicName");
					if (StringUtils.isNotBlank(value)) {
						setComponent(getComponentId().withGroup(value));
						int idx = value.lastIndexOf('.');
						if (idx != -1)
							setComponent(getComponentId().withGroup(value.substring(0, idx))
									.withId(value.substring(idx + 1)));
						isBundle = true;
					}

					if (isBundle) {
						return true;
					}
				}
			} catch (IOException ioe) {
				throw new HaltResolutionException(String.format("Could not resolve %s.", getComponentId()), ioe, this);
			}
		} catch (MalformedURLException | URISyntaxException murle) {
			throw new HaltResolutionException("Invalid archive URL.", murle, this);
		}
		return false;
	}

}
