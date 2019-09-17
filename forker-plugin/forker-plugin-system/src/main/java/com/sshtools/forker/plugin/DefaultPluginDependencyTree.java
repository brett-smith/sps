package com.sshtools.forker.plugin;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import com.sshtools.forker.plugin.api.ConflictStrategy;
import com.sshtools.forker.plugin.api.PluginArchive;
import com.sshtools.forker.plugin.api.PluginClasspath;
import com.sshtools.forker.plugin.api.PluginComponent;
import com.sshtools.forker.plugin.api.PluginComponentId;
import com.sshtools.forker.plugin.api.PluginDependencyTree;
import com.sshtools.forker.plugin.api.PluginManager;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;
import com.sshtools.forker.plugin.api.PluginProgressMonitor.MessageType;
import com.sshtools.forker.plugin.api.PluginSpec;
import com.sshtools.forker.plugin.api.ResolutionState;
import com.sshtools.forker.plugin.maven.MavenFolderArchive;
import com.sshtools.forker.plugin.maven.MavenJarArchive;

public class DefaultPluginDependencyTree implements PluginDependencyTree {

	final static ArtifactVersion ZERO_VERSION = new DefaultArtifactVersion("0.0.0");
	final static ArtifactVersion MAX_VERSION = new DefaultArtifactVersion("9999.99999.9999");

	interface PluginComponentIdFilter {
		void check(PluginComponentId id);

		boolean match(PluginComponentId id);
	}

	private PluginClasspath classpath;
	private Set<PluginArchive> failed = new LinkedHashSet<>();
	private PluginManager manager;
	private Set<PluginArchive> resolved = new LinkedHashSet<>();

	public DefaultPluginDependencyTree(PluginManager manager) {
		this(manager, new PluginClasspath() {
		});
	}

	public DefaultPluginDependencyTree(PluginManager manager, PluginClasspath classpath) {
		this.manager = manager;
		this.classpath = classpath;
	}

	protected PluginArchive merge(PluginArchive original, PluginArchive replacement) {
		if (original == replacement)
			return replacement;

		replacement.addDependent(original.getDependents().toArray(new PluginComponentId[0]));
		replacement.addDependency(original.getDependencies().toArray(new PluginComponentId[0]));
		for (PluginSpec spec : original.getChildren()) {
			spec.reparent(replacement);
			replacement.addChild(spec);
		}
		return replacement;
	}

	@Override
	public void add(PluginArchive archive, PluginProgressMonitor monitor) {

		ConflictStrategy strategy = manager.getResolutionContext().conflictStrategy();
		if (strategy == null)
			strategy = manager.getConflictStrategy();

		PluginArchive other = get(archive.getComponentId().idAndGroup(), PluginArchive.class);

		if (other == null) {
			/* This is the first time an archive with component ID has been added */
			addToResolved(null, archive);
			return;
		} else {
			/*
			 * Are they EXACTLY the same archive?. If so, just leave things as they are
			 */
			if (other == archive)
				return;
		}

		if (strategy == ConflictStrategy.ERROR) {
			if (other != null && !other.getComponentId().equals(archive.getComponentId())) {
				throw new IllegalStateException(String.format(
						"There is a dependency version conflict for %s. Both version %s and %s are currently required.",
						archive.getComponentId().idAndGroup(), archive.getComponentId().getVersion(),
						other.getComponentId().getVersion()));
			}
		} else if (strategy == ConflictStrategy.USE_BOTH) {
			/*
			 * If the versions are different, just add the new one regardless of resolution
			 * state. If they are the same, we use the best resolved one
			 */
			if (other != null && !other.getComponentId().equals(archive.getComponentId())) {
				addToResolved(null, archive);
				return;
			}
		}

		/*
		 * Decide on versionless behaviour. This will differ depending on the conflict
		 * resolution strategy
		 */
		ArtifactVersion versionless = ZERO_VERSION;
		if (strategy == ConflictStrategy.USE_EARLIEST)
			versionless = MAX_VERSION;

		/* Compare versions. */
		ArtifactVersion archiveVersion = archive.getComponentId().hasVersion()
				? new DefaultArtifactVersion(archive.getComponentId().getVersion())
				: versionless;
		ArtifactVersion otherVersion = other != null && other.getComponentId().hasVersion()
				? new DefaultArtifactVersion(other.getComponentId().getVersion())
				: versionless;

		/*
		 * Special case. Anything is better than a DefaultArchive, so if the existing
		 * one is such a thing, replace it with this better resolved one (probably a
		 * Maven jar). To do this, just treat it as versionless.
		 */
		if (other != null && other.getClass().equals(DefaultArchive.class)
				&& !archive.getClass().equals(DefaultArchive.class)) {
			otherVersion = versionless;
		}

		/* Now for the version comparison itself */
		int cmp = archiveVersion.compareTo(otherVersion);
		if (cmp == 0) {
			bestResolved(archive, monitor, other);
		} else if (cmp > 0) {
			/* The new archive is the newer one. */
			switch (strategy) {
			case USE_EARLIEST:
				/* The existing archive should be left alone */
				break;
			case USE_LATEST:
				/* The new archive should replace the existing one */
				replaceArchiveWithAnother(other, archive, monitor);
				break;
			default:
				bestResolved(archive, monitor, other);
				break;
			}
		} else if (cmp < 0) {
			/* The existing archive is the newer one. */
			switch (strategy) {
			case USE_EARLIEST:
				/* The new archive should be replace the existing one */
				replaceArchiveWithAnother(other, archive, monitor);
				break;
			case USE_LATEST:
				/* The existing archive should be left alone */
				break;
			default:
				bestResolved(archive, monitor, other);
				break;
			}
		}
	}

	protected void bestResolved(PluginArchive archive, PluginProgressMonitor monitor, PluginArchive other) {
		/* Same version. Decide based on who is the most resolved **/
		if (archive.getState().isResolved()) {
			if (other != null && other.getState().isResolved()) {
				/* Both are resolved enough, leave things as they are */
			} else {
				/* New archive is resolved, existing one isn't, replace with the new one */
				replaceArchiveWithAnother(other, archive, monitor);
			}
		} else {
			if (other != null && other.getState().isResolved()) {
				/*
				 * The existing archive is resolved, but this one isn't. Leave things as they
				 * are
				 */
			} else {
				/* Neither are resolved, use this newer archive anyway */
				replaceArchiveWithAnother(other, archive, monitor);
			}
		}
	}

	protected void replaceArchiveWithAnother(PluginArchive original, PluginArchive replacement,
			PluginProgressMonitor monitor) {

		URL exUrl = original != null ? original.getArchive() : null;
		URL arcUrl = replacement.getArchive();
		if (exUrl != null && arcUrl != null && !arcUrl.equals(exUrl)) {
			/*
			 * Two archives with same component ID but different classpath locations. This
			 * situation should be rare, but may occur in a development environment where
			 * you might want one particular archive to come from a class folder that you
			 * are actively working, but it has a lot of it's dependencies in a shade jar
			 * (i.e. the plugin shell itself does this!).
			 * 
			 * In this case, we use the archive that is a real jar, but add other archives
			 * folder location to it's pre-classpath (TODO)
			 */
			if (exUrl.getPath().endsWith(".jar") && !arcUrl.getPath().endsWith(".jar")) {
				/* Use the existing one as our base, but add to it's classpath */
				// TODO merge existing classpath?
				original.setClasspath(Arrays.asList(arcUrl, exUrl));
				replacement.setClasspath(Arrays.asList(arcUrl, exUrl));
			} else if (arcUrl.getPath().endsWith(".jar") && !exUrl.getPath().endsWith(".jar")) {
				/* Use the new one as our base */
				// TODO merge existing classpath?
				original.setClasspath(Arrays.asList(exUrl, arcUrl));
				replacement.setClasspath(Arrays.asList(exUrl, arcUrl));
			} else {
				/* Both folders or both jars. Use the newest one */
				try {
					URL ur = PluginUtils
							.mostRecentlyModified(PluginUtils.urlToFile(exUrl), PluginUtils.urlToFile(arcUrl)).toURI()
							.toURL();
					original.setClasspath(Arrays.asList(ur));
					replacement.setClasspath(Arrays.asList(ur));
					monitor.message(MessageType.INFO, String.format(
							"Two different archives have been found on the classpath for %s. Both %s, and %s are on the classpath. Using the newer %s",
							replacement.getComponentId(), PluginUtils.trimUrl(arcUrl), PluginUtils.trimUrl(exUrl),
							PluginUtils.trimUrl(ur)));
				} catch (MalformedURLException murle) {
					throw new IllegalStateException("Failed to merge classpath.", murle);
				}
			}
		}

		addToResolved(original, replacement);
	}

	protected void addToResolved(PluginArchive original, PluginArchive replacement) {

		/* Add replacement */
		if (original == null)
			resolved.add(replacement);
		else {
			/* Remove original and merge replacement */
			resolved.remove(original);
			resolved.add(merge(original, replacement));
		}

		/* Just in case */
		failed.remove(original);
		failed.remove(replacement);
	}

	public PluginArchive addFile(PluginProgressMonitor monitor, File file) throws IOException {
		AbstractArchive arc;
		URL url = file.toURI().toURL();
		if (file.getName().equals("")) {
			file = new File(System.getProperty("user.dir"));
		}
		if (file.isDirectory()) {
			File pom = new File(file.getParentFile(), file.getName() + ".pom");
			if (pom.exists())
				arc = new MavenFolderArchive(manager, file, pom, url, monitor);
			else {
				pom = new File(file.getParentFile().getParentFile(), "pom.xml");
				if (pom.exists()) {
					arc = new MavenFolderArchive(manager, file, pom, url, monitor);
				} else {
					arc = new FolderArchive(manager, url);
				}
			}
		} else if (file.isFile()) {
			if (file.getName().toLowerCase().endsWith(".xml")) {
				arc = new FeatureArchive(manager, url, monitor, false);
			} else if (file.getName().toLowerCase().endsWith(".jar") || file.getName().toLowerCase().endsWith(".zip")) {

				try {
					arc = new MavenJarArchive(manager, url, monitor);
				} catch (IllegalArgumentException iae) {
//				try {
//					arc = new BundleJarArchive(manager, url);
//				} catch (IllegalArgumentException iae2) {
					arc = new JarArchive(manager, url);
//				}
				}
				arc.setSize(file.length());
				arc.setHash(Hashing.sha1(file));
			} else
				throw new IllegalArgumentException(String.format("Unknown CLASSPATH element. %s", file));

		} else
			return null;

		if (arc != null && arc.getComponentId().getId().equals("org.eclipse.jdt.launching.javaagent")) {
			monitor.message(MessageType.INFO,
					String.format("Skipping Eclipse Debug Jar %s", arc.getArchive()));
			return null;
		}

		add(arc, monitor);
		return arc;
	}

	@SuppressWarnings("unchecked")
	protected <T extends PluginComponent<?>> T get(Set<? extends PluginComponent<?>> archives, PluginComponentId key,
			Class<T> clazz) {

		if (clazz.equals(PluginSpec.class) && !key.hasPlugin())
			throw new IllegalArgumentException(String.format(
					"The plugin class must be part of the ID %s to be able to locate a plugin specification.", key));
		if (clazz.equals(PluginArchive.class) && key.hasPlugin())
			throw new IllegalArgumentException(String.format(
					"No plugin class must be present in the ID %s to be able to locate a plugin archive.", key));

		boolean grpm;
		boolean idm;
		boolean verm;
		boolean plugm;
		boolean typem;
		for (PluginComponent<?> a : archives) {
			grpm = Objects.equals(a.getComponentId().getGroup(), key.getGroup());
			idm = Objects.equals(a.getComponentId().getId(), key.getId());
			verm = !key.hasVersion() || (a.getComponentId().hasVersion()
					&& Objects.equals(a.getComponentId().getVersion(), key.getVersion()));
			plugm = !key.hasPlugin() || Objects.equals(a.getComponentId().getPlugin(), key.getPlugin());
			typem = Objects.equals(a.getComponentId().getType(), key.getType());
			if (grpm && idm && verm && plugm && typem)
				return (T) a;
			else if (a instanceof PluginArchive) {
				T t = get(((PluginArchive) a).getChildren(), key, clazz);
				if (t != null)
					return t;
			}
		}
		return null;
	}

	protected <T extends PluginComponent<?>> Set<T> list(Set<? extends PluginComponent<?>> components,
			PluginComponentId key, Class<T> clazz, Set<T> collection) {
		return list(components, key, clazz, collection, new PluginComponentIdFilter() {

			@Override
			public void check(PluginComponentId id) {
				if (clazz.equals(PluginSpec.class) && !key.hasPlugin())
					throw new IllegalArgumentException(String.format(
							"The plugin class must be part of the ID %s to be able to locate a plugin specification.",
							key));
				if (clazz.equals(PluginArchive.class) && key.hasPlugin())
					throw new IllegalArgumentException(String.format(
							"No plugin class must be present in the ID %s to be able to locate a plugin archive.",
							key));

			}

			@Override
			public boolean match(PluginComponentId id) {
				boolean grpm = Objects.equals(id.getGroup(), key.getGroup());
				boolean idm = Objects.equals(id.getId(), key.getId());
				boolean verm = !key.hasVersion()
						|| (id.hasVersion() && Objects.equals(id.getVersion(), key.getVersion()));
				boolean plugm = !key.hasPlugin() || Objects.equals(id.getPlugin(), key.getPlugin());
				return grpm && idm && verm && plugm;
			}
		});
	}

	@SuppressWarnings("unchecked")
	protected <T extends PluginComponent<?>> Set<T> list(Set<? extends PluginComponent<?>> components,
			PluginComponentId key, Class<T> clazz, Set<T> collection, PluginComponentIdFilter filter) {
		filter.check(key);
		for (PluginComponent<?> a : components) {
			if (filter.match(a.getComponentId())) {
				collection.add((T) a);
			}
			if (a instanceof PluginArchive && clazz.isAssignableFrom(PluginSpec.class))
				list(((PluginArchive) a).getChildren(), key, clazz, collection);
		}
		return collection;
	}

	@Override
	public <T extends PluginComponent<?>> T get(PluginComponentId key, Class<T> clazz) {
		return get(resolved, key, clazz);
	}

	@Override
	public <T extends PluginComponent<?>> T newest(PluginComponentId key, Class<T> clazz) {
		LinkedHashSet<T> comps = new LinkedHashSet<>();
		list(resolved, key, clazz, comps);
		T newest = null;
		ArtifactVersion newestVersion = null;
		for (T t : comps) {
			ArtifactVersion thisVersion = t.getComponentId().hasVersion()
					? new DefaultArtifactVersion(t.getComponentId().getVersion())
					: new DefaultArtifactVersion("0.0.0");
			if (newestVersion == null || thisVersion.compareTo(newestVersion) > 0) {
				newest = t;
				newestVersion = thisVersion;
			}
		}
		return newest;
	}

	@Override
	public <T extends PluginComponent<?>> Set<T> list(PluginComponentId key, Class<T> clazz) {
		return list(resolved, key, clazz, new LinkedHashSet<>());
	}

	@Override
	public PluginClasspath getClassPath() {
		return classpath;
	}

	@Override
	public Set<PluginArchive> getResolved() {
		return Collections.unmodifiableSet(resolved);
	}

	public Set<PluginSpec> getUnresolvedPlugins() {
		Set<PluginSpec> r = new LinkedHashSet<>();
		for (PluginArchive child : resolved) {
			for (PluginSpec spec : child.getChildren()) {
				if (!spec.getState().isResolved())
					r.add(spec);
			}
		}
		return r;
	}

	@Override
	public void remove(PluginArchive archive) {
		resolved.remove(archive);
		failed.remove(archive);
	}

	@Override
	public Set<PluginArchive> resolve(PluginProgressMonitor monitor, boolean reset) throws IOException {

		if (monitor != null)
			monitor.start(3);

		try {
			String[] paths = getClassPath().getPath();

			if (reset)
				resolved.clear();
			failed.clear();

			/**
			 * Task 1
			 * 
			 * Add everything on the classpath. This includes the sytem classpath, any
			 * additional archives added to the plugin managers manual archive list, and
			 * anything in the local repository (where stuff gets installed)
			 * 
			 * After this first pass, all archives that are standalone (i.e. no dependencies
			 * will be fully resolved. Anything with dependencies will be unresolved until
			 * it's dependencies are resolved.
			 */
			if (monitor != null) {
				monitor.progress(1, "Resolving root archives");
				monitor.start(paths.length);
			}
			try {
				for (int i = 0; i < paths.length; i++) {
					File file = new File(paths[i]);
					monitor.message(MessageType.INFO,
							String.format("Adding root archive %s", PluginUtils.trimFileName(file)));
					PluginArchive arc = addFile(monitor, file);
					if (arc != null)
						monitor.progress(i + 1, arc.getComponentId().toString());
				}
			} finally {
				monitor.end();
			}

			Set<PluginArchive> stopResolving = new LinkedHashSet<>();

			/*
			 * Task 2 - X
			 * 
			 * Now everything that wasn't completely resolved
			 *
			 **/

			Set<PluginArchive> toResolve = new LinkedHashSet<>(resolved);
			Set<PluginArchive> failed = new LinkedHashSet<>();
			int pass = 0;

			failed.clear();
			while (true) {

				/* What should we try to resolve this round */
				toResolve.clear();
				toResolve = new LinkedHashSet<>(resolved);
				toResolve.removeAll(stopResolving);
				toResolve.removeAll(failed);

				if (toResolve.isEmpty())
					break;

				pass++;

				/*
				 * Only resolve if number of resolve archives has changed in any way, or if the
				 * resolution state for any of those has change.
				 * 
				 * Do at least 2 passes as the first pass may result in no changes.
				 */
				if (monitor != null) {
					monitor.changeTotal(pass + 2);
					monitor.progress(pass + 1, "Resolve pass " + pass);
					monitor.start(toResolve.size());
				}
				try {
					if (pass > 1000) {
						monitor.message(MessageType.ERROR, "Cycle involving "
								+ String.join(",", PluginComponentId.toString(PluginUtils.toIds(toResolve))));
						break;
					}

					int p = 0;
					for (PluginArchive archive : toResolve) {

						ResolutionState state = archive.getState();
						monitor.message(MessageType.INFO, String.format(String.format("%s(%-10s) %s",
								PluginUtils.spaces(pass), state, archive.getComponentId())));

						if (monitor != null)
							monitor.progress(++p, archive.getComponentId().toString());
						if (!state.isResolved()) {
							try {
								archive.resolve(monitor);
							} catch (HaltResolutionException ise) {
								// Will not be resolvable, don't try again
								failed.addAll(ise.getArchives());
							} catch (ResolutionRetryException rre) {
								// Make sure we retry
								failed.removeAll(rre.getArchives());
							}
						} else {
							stopResolving.add(archive);
						}
					}
				} finally {
					monitor.end();
				}
			}

			if (monitor != null) {
				for (PluginArchive failure : failed) {
					if (!failure.getComponentId().isOptional() || manager.getResolutionContext().resolveOptional())
						monitor.message(MessageType.WARNING,
								String.format("Failed to resolve %s", failure.getComponentId()));
				}
			}

			/*
			 * Task X + 1
			 * 
			 * Final pass to autostart plugins
			 */
//			if (manager.getResolutionContext().startAutostartPlugins()
//					|| manager.getResolutionContext().forceStartPlugins()) {
//				int p = 0;
//				if (monitor != null) {
//					monitor.progress(pass + 1, "Autostarting plugins");
//					monitor.start(resolved.size());
//				}
//				try {
//					for (PluginArchive archive : resolved) {
//						if (monitor != null)
//							monitor.progress(++p, archive.getComponentId().toString());
//						for (PluginSpec spec : archive.getChildren()) {
//							if ((spec.isAutostart() || manager.getResolutionContext().forceStartPlugins())
//									&& spec.getState().isUsable() && !manager.isStarted(spec.getComponentId())) {
//								manager.start(spec.getComponentId(), monitor,
//										manager.getResolutionContext().arguments());
//							}
//						}
//					}
//				} finally {
//					if (monitor != null)
//						monitor.end();
//				}
//			}

			resolved.addAll(failed);
		} finally {
			if (monitor != null)
				monitor.end();
		}

		return resolved;

	}

}
