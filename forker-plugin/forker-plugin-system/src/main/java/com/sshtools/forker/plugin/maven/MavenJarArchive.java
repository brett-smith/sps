package com.sshtools.forker.plugin.maven;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.io.FilenameUtils;

import com.sshtools.forker.plugin.AbstractArchiveWithDependencies;
import com.sshtools.forker.plugin.DefaultArchive;
import com.sshtools.forker.plugin.HaltResolutionException;
import com.sshtools.forker.plugin.JarArchive;
import com.sshtools.forker.plugin.ResolutionRetryException;
import com.sshtools.forker.plugin.api.PluginArchive;
import com.sshtools.forker.plugin.api.PluginComponentId;
import com.sshtools.forker.plugin.api.PluginDependencyTree;
import com.sshtools.forker.plugin.api.PluginManager;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;
import com.sshtools.forker.plugin.api.PluginScope;
import com.sshtools.forker.plugin.api.ResolutionState;

public class MavenJarArchive extends AbstractArchiveWithDependencies {

	private boolean dependenciesResolved;
	private PluginArchive embedder;

	public MavenJarArchive(PluginManager manager, URL archive, PluginProgressMonitor monitor) {
		super(manager, archive);
		if (!readPOM(false, monitor))
			throw new IllegalArgumentException(String.format("%s is not a Maven Jar.", getArchive()));
	}

	public MavenJarArchive(PluginManager manager, PluginComponentId spec) {
		super(manager, null, spec);
	}

	MavenJarArchive(PluginArchive parent, PluginComponentId spec, PluginProgressMonitor monitor) {
		super(parent.getParent(), null, spec);
		setArchive(parent.getArchive());
		embedder = parent;
		if (!readPOM(false, monitor))
			throw new IllegalArgumentException(String.format("%s is not a Maven Jar for %s.", getArchive(), spec));
	}

	@Override
	public PluginScope getScope() {
		PluginArchive emb = getEmbedder();
		if (emb != null)
			return emb.getScope();
		else
			return super.getScope();
	}

	@Override
	public PluginArchive getEmbedder() {
		while (embedder != null) {
			PluginArchive e = embedder.getEmbedder();
			if (e == null)
				break;
			else
				embedder = e;
		}
		return embedder;
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
					if (base.isResolved()) {
						base = calcChildrenState();
						if (base.isResolved()) {
							base = calcNodeState().least(base);
						}
					}
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
				resolvePOM(monitor);
			} catch (IllegalArgumentException iae) {
				if (embedder != null)
					throw iae;
				else {
					/*
					 * A Maven archive declared a dependency that exists but does not have any Maven
					 * meta-data in it. So we morph into a plain jar
					 */
					JarArchive jararc = new JarArchive(getManager(), getArchive());
					PluginDependencyTree depTree = getManager().getResolutionContext().getDependencyTree();
					depTree.remove(this);
					depTree.add(jararc, monitor);
				}
			}
			return;
		}

		resolvePluginsState(monitor);

		if (!calcNodeState().isComplete())
			resolveNode(monitor);

		resolveChildren(monitor);

	}

	protected final ResolutionState calcPOMState() {
		return dependenciesResolved ? ResolutionState.RESOLVED : ResolutionState.UNRESOLVED;
	}

	protected void resolvePOM(PluginProgressMonitor monitor) throws IOException {
		if (getArchive() == null) {
			if (!getComponentId().isOptional() && getManager().getResolutionContext().failOnError())
				throw new ResolutionRetryException(
						String.format("Archive for %s not known, cannot resolve.", getComponentId()), this);
		} else if (!readPOM(true, monitor)) {
			throw new HaltResolutionException(String.format("%s is not a Maven Jar.", getArchive()), this);
		}
		dependenciesResolved = true;
		getManager().getResolutionContext().getDependencyTree().add(this, monitor);
	}

	protected boolean readPOM(boolean deps, PluginProgressMonitor monitor) {
		try {

			File file = new File(getArchive().toURI().toURL().getPath());

			try (JarFile jf = new JarFile(file)) {

				if (embedder != null) {
					/* If embedded, we already know the component ID so can directly locate it */
					String entryName = "META-INF/maven/" + getComponentId().getGroup() + "/" + getComponentId().getId()
							+ "/pom.xml";
					JarEntry jarEntry = jf.getJarEntry(entryName);
					if (jarEntry != null) {
						/* Assume any dependencies are embedded too */
						new POMReader().resolveDependencies(deps).embedded(true).readPOM(this,
								jf.getInputStream(jarEntry), monitor);
						return true;
					}
				} else {
					Enumeration<JarEntry> entries = jf.entries();
					String archiveBase = FilenameUtils.getBaseName(getArchive().getPath());

					DefaultArchive dummy = new DefaultArchive(getManager(), getArchive(), getComponentId());

					JarEntry firstEntry = null;
					JarEntry thisEntry = null;
					Set<JarEntry> otherEntries = new LinkedHashSet<>();
					int poms = 0;
					while (entries.hasMoreElements()) {
						JarEntry jarEntry = (JarEntry) entries.nextElement();

						if (jarEntry.getName().startsWith("META-INF/maven/")
								&& jarEntry.getName().endsWith("/pom.xml")) {

							try {
								/* Just read the main details, not the deps */
								new POMReader().resolveDependencies(false).readPOM(dummy, jf.getInputStream(jarEntry), monitor);

								poms++;

								if (firstEntry == null)
									firstEntry = jarEntry;

								/*
								 * Does this look like the Jar name? This only works for our component ID style
								 * jars (i.e. group@id@version)
								 */
								if (dummy.getComponentId().toFilename().equals(archiveBase)) {
									thisEntry = jarEntry;
								} else
									otherEntries.add(jarEntry);
							} catch (IllegalArgumentException ise) {
								// Not a maven jar
							}
						}
					}

					if (thisEntry != null) {
						/*
						 * Now read the dependencies. They are considered embedded (i.e. shaded) if
						 * there was more than one pom found insde
						 */
						new POMReader().resolveDependencies(deps).embedded(poms > 1).readPOM(this,
								jf.getInputStream(thisEntry), monitor);
						resolveOthers(deps, jf, otherEntries, monitor);
						return true;
					}

					/*
					 * Is there a .pom file at the same location? When we download Maven artifacts
					 * from a remote, we also get the POM, for Jars that are built within them
					 * included (it does happen).
					 * 
					 * This is the preferred method as we do not need to take care of shaded jars
					 * that have multiple POM metadata inside
					 */
					File pomFile = new File(file.getParentFile(),
							file.getName().substring(0, file.getName().length() - 4) + ".pom");
					if (pomFile.exists()) {
						try (InputStream in = new FileInputStream(pomFile)) {
							new POMReader().resolveDependencies(deps).readPOM(this, in, monitor);
							resolveOthers(deps, jf, otherEntries, monitor);
							return true;
						} catch (IOException ioe) {
							throw new HaltResolutionException(String.format("Could not resolve %s.", getComponentId()),
									ioe, this);
						}
					}

					/**
					 * Fall back to the first entry in the Jar (if any). Assume this is a shaded jar
					 * with all dependencies embedded.
					 */
					if (firstEntry != null) {
						otherEntries.remove(firstEntry);
						new POMReader().resolveDependencies(deps).embedded(poms > 1).readPOM(this,
								jf.getInputStream(firstEntry), monitor);
						resolveOthers(deps, jf, otherEntries, monitor);
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

	protected void resolveOthers(boolean deps, JarFile jf, Set<JarEntry> otherEntries, PluginProgressMonitor monitor) throws IOException {
		for (JarEntry other : otherEntries) {
			MavenJarArchive arc = new MavenJarArchive(this, getComponentId(), monitor);
			new POMReader().resolveDependencies(deps).embedded(true).readPOM(arc, jf.getInputStream(other), monitor);
			getManager().getResolutionContext().getDependencyTree().add(arc, monitor);
		}
	}

}
