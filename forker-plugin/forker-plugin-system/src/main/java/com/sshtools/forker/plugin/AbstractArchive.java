package com.sshtools.forker.plugin;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import com.sshtools.forker.plugin.api.ConflictStrategy;
import com.sshtools.forker.plugin.api.Plugin;
import com.sshtools.forker.plugin.api.PluginArchive;
import com.sshtools.forker.plugin.api.PluginComponentId;
import com.sshtools.forker.plugin.api.PluginManager;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;
import com.sshtools.forker.plugin.api.PluginProgressMonitor.MessageType;
import com.sshtools.forker.plugin.api.PluginRemote;
import com.sshtools.forker.plugin.api.PluginResolveContext;
import com.sshtools.forker.plugin.api.PluginScope;
import com.sshtools.forker.plugin.api.PluginSpec;
import com.sshtools.forker.plugin.api.ResolutionState;

public abstract class AbstractArchive extends AbstractContainer<PluginManager, PluginSpec> implements PluginArchive {

	private URL archive;
	private Collection<URL> classpath;
	private String hash;
	private boolean pluginsResolved;
	private long size;
	private Set<PluginSpec> specs = new LinkedHashSet<>();
	private ClassLoader classLoader;

	public AbstractArchive(PluginManager manager, URL archive) {
		this(manager, archive, PluginComponentId.fromURL(archive));
	}

	public AbstractArchive(PluginManager manager, URL archive, PluginComponentId component) {
		super(manager, component);
		this.archive = archive;
	}

	@Override
	public void delete() throws IOException {
		if (!getState().isResolved()) {
			throw new IllegalStateException(String.format("Cannot delete an unresolved maven Jar.", getComponentId()));
		}
		URL arc = getArchive();
		if (getScope().equals(PluginScope.INSTALLED))
			throw new IOException(String.format("Can only delete archives with a scope of %s. %s is %s",
					PluginScope.INSTALLED, getComponentId(), getScope()));
		if (!PluginUtils.urlToFile(arc).delete())
			throw new IOException(String.format("Could not delete %s", arc));
	}

	@Override
	public final URL getArchive() {
		return archive;
	}

	@Override
	public boolean addChild(PluginSpec... child) {
		boolean added = false;
		for (PluginSpec c : child)
			added = specs.add(c) || added;
		if (added)
			dirtyState();
		return added;
	}

	@Override
	public boolean removeChild(PluginSpec... child) {
		boolean removed = false;
		for (PluginSpec c : child) {
			removed = specs.remove(c) || removed;
		}
		if (removed)
			dirtyState();
		return removed;
	}

	@Override
	public final Set<PluginSpec> getChildren() {
		return Collections.unmodifiableSet(specs);
	}

	@Override
	public PluginScope getScope() {
		URL arc = getArchive();
		if (arc == null)
			return PluginScope.UNREAL;
		else {
			try {
				File arcf = PluginUtils.urlToFile(arc);
				if (PluginUtils.isDescendant(getManager().getLocal(), arcf)) {
					if (arcf.exists())
						return PluginScope.INSTALLED;
					else
						return PluginScope.UNREAL;
				} else
					return PluginScope.SYSTEM;
			} catch (IllegalArgumentException iae) {
				return PluginScope.REMOTE;
			} catch (IOException e) {
				return PluginScope.UNREAL;
			}
		}
	}

	@Override
	public final ClassLoader getClassLoader() {
		PluginArchive embedded = getEmbedder();
		if (embedded != null) {
			ClassLoader ecl = embedded.getClassLoader();
			if (ecl == null)
				throw new IllegalStateException("How?");
			return ecl;
		}

		if (classLoader == null) {
			switch (getScope()) {
			case SYSTEM:
				classLoader = getManager().getClassLoader();
				break;
			case INSTALLED:
				// ArchiveClassLoader jarLoader = new
				// ArchiveClassLoader(getManager().getClassLoader(), this);
				PluginClassLoader jarLoader = new PluginClassLoader(this, getManager().getClassLoader());
				classLoader = jarLoader;
				break;
			default:
				// Unreal, not attached to anything
				classLoader = getManager().getClassLoader();
				break;
			}
		}
		return classLoader;
	}

	@Override
	public final Collection<URL> getClasspath() {
		if (classpath != null)
			return classpath;
		URL archive = getArchive();
		if (archive == null)
			return Collections.emptyList();
		else
			return Arrays.asList(archive);
	}

	@Override
	public PluginArchive getEmbedder() {
		return null;
	}

	public String getHash() {
		PluginArchive e = getEmbedder();
		if (e != null)
			return e.getHash();
		else
			return hash;
	}

	@Override
	public PluginManager getParent() {
		return getManager();
	}

	public Set<String> getPseudoClasspath() {
		try {
			Set<String> cp = new LinkedHashSet<>();
			for (URL u : getClasspath())
				cp.add(PluginUtils.getPathRelativeToCwd(u));
			for (PluginComponentId id : getAllDependencies()) {
				PluginArchive parc = getManager().getResolutionContext().getDependencyTree().get(id,
						PluginArchive.class);
				if (parc != null) {
					for (URL u : parc.getClasspath())
						cp.add(PluginUtils.getPathRelativeToCwd(u));
				}
			}
			return cp;
		} catch (StackOverflowError soe) {
			return new LinkedHashSet<>(Arrays.asList("Circular Problem with " + getComponentId()));
		}
	}

	public long getSize() {
		PluginArchive e = getEmbedder();
		if (e != null)
			return e.getSize();
		else
			return size;
	}

	@Override
	protected ResolutionState calcState() {
		ResolutionState base = calcBaseState();
		if (base.isResolved()) {
			base = calcArchiveState();
			if (base.isResolved()) {
				base = calcPluginsState();
				if (base.isResolved())
					base = calcChildrenState();
			}
		}
		return base;
	}

	@Override
	public final void resetClassLoader() {
		for (PluginSpec siblingSpec : getChildren()) {
			if (getManager().isStarted(siblingSpec.getComponentId())) {
				throw new IllegalStateException(String.format(
						"Cannot reset classloader for %s until all it's plugins are stopped.", getComponentId()));
			}
		}
		try {
			ClassLoader cl = getClassLoader();
			if (cl instanceof PluginClassLoader)
				try {
					((PluginClassLoader) cl).close();
				} catch (IOException e) {
				}
		} finally {
			classLoader = null;
			dirtyState();
		}
		for (PluginSpec spec : getChildren())
			spec.resetClassLoader();

	}

	@Override
	protected void doResolve(PluginProgressMonitor progress) throws IOException {
		ResolutionState base = calcBaseState();
		if (base.isResolved()) {
			if (!calcArchiveState().isResolved()) {
				resolveArchive(progress);
			}
			resolvePluginsState(progress);
		}

	}

	public final void setArchive(URL archive) {
		this.archive = archive;
		dirtyState();
	}

	public final void setClasspath(Collection<URL> classpath) {
		this.classpath = classpath;
		dirtyState();
	}

	public final void setHash(String hash) {
		this.hash = hash;
	}

	public final void setSize(long size) {
		this.size = size;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [component=" + getComponentId() + ", archive=" + archive + ", size="
				+ getSize() + ", hash=" + getHash() + ", scope=" + getScope() + ", dependants=["
				+ String.join(",", PluginComponentId.toString(getDependents())) + "], dependencies=["
				+ String.join(",", PluginComponentId.toString(getDependencies())) + "]]";
	}

	protected void addCp(URL part, Set<String> cp, Set<PluginComponentId> ids) {
		String path = PluginUtils.getPathRelativeToCwd(part);
		cp.add(path);
		for (PluginComponentId id : ids) {
			PluginArchive arc = getManager().getResolutionContext().getDependencyTree().get(id, PluginArchive.class);
			if (arc != null && arc.getArchive() != null) {
				addCp(arc.getArchive(), cp, arc.getDependencies());
			}
		}
	}

	protected final ResolutionState calcArchiveState() {
		PluginArchive embedder = getEmbedder();
		if (archive == null && (embedder == null || embedder.getArchive() == null))
			return ResolutionState.UNRESOLVED;
		try {
			File urlToFile = PluginUtils.urlToFile(archive);
			return urlToFile.exists() ? ResolutionState.RESOLVED : ResolutionState.UNRESOLVED;
		} catch (IllegalArgumentException iae) {
			return ResolutionState.RESOLVED;
		}
	}

	protected final ResolutionState calcPluginsState() {
		return (pluginsResolved || !getManager().getResolutionContext().resolvePlugins()) ? ResolutionState.RESOLVED
				: ResolutionState.UNRESOLVED;
	}

	protected void resolveArchive(PluginProgressMonitor progress) {

		if (getComponentId().isValid() && getManager().getResolutionContext().download()
				&& (!getComponentId().isOptional()
						|| (getComponentId().isOptional() && getManager().getResolutionContext().resolveOptional()))) {
			try {
				URL url = getManager().downloadArchive(progress, getComponentId().asOptional(false));
				if (url != null) {
					setArchive(url);
					for (PluginComponentId par : getDependents()) {
						PluginArchive arc = getManager().getResolutionContext().getDependencyTree().get(par,
								PluginArchive.class);
						if (arc != null)
							arc.resetClassLoader();
					}
					return;
				}
			} catch (IOException ioe) {
				throw new HaltResolutionException(String.format("Failed to download %s", getComponentId()), ioe, this);
			}
		}
		throw new HaltResolutionException(String.format("Failed to download %s", getComponentId()), this);
	}

	protected void resolvePluginsState(PluginProgressMonitor progress) {
		PluginResolveContext ctx = getManager().getResolutionContext();
		ConflictStrategy strategy = ctx.conflictStrategy();
		if (strategy == null)
			strategy = getManager().getConflictStrategy();

		// TODO temp ... stop resolving plugins at runtime?
		if (!ctx.resolvePlugins() || true) {
			/*
			 * If the classpath is empty, this is a list for available plugins, so we are
			 * not actually loading plugins
			 */
			pluginsResolved = true;
			return;
		}

		/**
		 * If there is more than one version of an archive, plugins may only be loaded
		 * from the highest version.
		 * 
		 * The older versions will not have any plugins, and so be candidates for
		 * cleaning up providing no other archives explicitly depend on them.
		 */
		PluginArchive newest = null;
		DefaultArtifactVersion thisVersion = new DefaultArtifactVersion(getComponentId().getVersion());
		DefaultArtifactVersion newestVersion = null;

		Set<PluginArchive> otherArchives = ctx.getDependencyTree().list(getComponentId().idAndGroup(),
				PluginArchive.class);
		for (PluginArchive other : otherArchives) {
			DefaultArtifactVersion otherVersion = new DefaultArtifactVersion(other.getComponentId().getVersion());
			if (otherVersion.compareTo(thisVersion) > 0) {
				newest = other;
				newestVersion = otherVersion;
			}
		}
		otherArchives.remove(this);
		if (newest != null && strategy != ConflictStrategy.USE_BOTH) {
			if (!thisVersion.equals(newestVersion)) {
				progress.message(MessageType.INFO, String.format("Version %s of %s superceeds versions %s",
						newestVersion, thisVersion, getComponentId()));

				for (PluginComponentId cid : getDependents()) {
					PluginArchive arc = ctx.getDependencyTree().get(cid, PluginArchive.class);
					if (arc != null) {
						arc.removeDependency(getComponentId());
						arc.addDependency(newest.getComponentId());

						/*
						 * Go through all of the dependents plugins depedencies, to see if any of those
						 * need to adjusted.
						 * 
						 * First remove the plugins in this archives
						 */
						for (PluginSpec thisSpec : getChildren()) {
							for (PluginSpec arcspec : arc.getChildren()) {
								arcspec.removeDependency(thisSpec.getComponentId());
							}
						}
						for (PluginSpec thisSpec : newest.getChildren()) {
							for (PluginSpec arcspec : arc.getChildren()) {
								arcspec.addDependency(thisSpec.getComponentId());
							}
						}
					}
				}
				removeChild(getChildren().toArray(new PluginSpec[0]));
				newest.addDependent(getDependents().toArray(new PluginComponentId[0]));
				removeDependent(getDependents().toArray(new PluginComponentId[0]));
				pluginsResolved = true;
				return;
			}
		}
		pluginsResolved = true;

		try {
			List<String> includes = getManager().getPluginInclude();
			List<String> excludes = getManager().getPluginExclude();
			boolean process = true;
			if (!includes.isEmpty()) {
				if (!matches(includes, getComponentId().toFilename()))
					process = false;
			}
			if (process && !excludes.isEmpty())
				process = !matches(excludes, getComponentId().toFilename());

			if (process) {
				progress.message(MessageType.INFO, String.format("Looking for plugins in %s", getComponentId()));
				Set<PluginSpec> l = new LinkedHashSet<>();
				new AnnotationFinder(getClassLoader(), getClasspath()) {
					@Override
					protected void onPlugin(Class<?> clazz, Plugin plugin) throws IOException {
						DefaultPluginSpec spec = new DefaultPluginSpec(AbstractArchive.this, clazz.getName(),
								plugin.dependencies(), plugin.name(), plugin.description());
						switch (plugin.start()) {
						case AUTO:
							spec.setAutostart(true);
							break;
						case MANUAL:
							spec.setAutostart(false);
							break;
						case DEFAULT:
							spec.setAutostart(getParent().isAutostart());
							break;
						}
						l.add(spec);
					}
				}.stopOnClassNotFound(false).stopOnOtherErrors(false).find(progress);
				addChild(l.toArray(new PluginSpec[0]));
				progress.message(MessageType.INFO, String.format("Found %d plugins in %s", l.size(), getComponentId()));
			}

			pluginsResolved = true;
		} catch (ClassNotFoundException | RuntimeException ise) {
			// Cannot index this jar for some reason
//			pluginsResolved = true;
			if (ctx.failOnError()) {
				if (ise instanceof RuntimeException)
					throw (RuntimeException) ise;
				else
					throw new IllegalStateException(String.format("Cannot find plugins. %s", getComponentId()), ise);
			} else if (progress != null)
				progress.message(MessageType.INFO, ise.getMessage());
		}
	}

	private boolean matches(List<String> patterns, String string) {
		for (String p : patterns) {
			if (string.matches("^" + p + ".*"))
				return true;
		}
		return false;
	}

}
