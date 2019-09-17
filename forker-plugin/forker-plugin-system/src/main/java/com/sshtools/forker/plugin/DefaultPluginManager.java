package com.sshtools.forker.plugin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import com.sshtools.forker.plugin.api.ConflictStrategy;
import com.sshtools.forker.plugin.api.InstallMode;
import com.sshtools.forker.plugin.api.Manager;
import com.sshtools.forker.plugin.api.PluginArchive;
import com.sshtools.forker.plugin.api.PluginClasspath;
import com.sshtools.forker.plugin.api.PluginComponent;
import com.sshtools.forker.plugin.api.PluginComponentId;
import com.sshtools.forker.plugin.api.PluginDependencyTree;
import com.sshtools.forker.plugin.api.PluginEntryPoint;
import com.sshtools.forker.plugin.api.PluginEntryPointExecutor;
import com.sshtools.forker.plugin.api.PluginEvent;
import com.sshtools.forker.plugin.api.PluginLifecycle;
import com.sshtools.forker.plugin.api.PluginManager;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;
import com.sshtools.forker.plugin.api.PluginProgressMonitor.MessageType;
import com.sshtools.forker.plugin.api.PluginRemote;
import com.sshtools.forker.plugin.api.PluginResolveContext;
import com.sshtools.forker.plugin.api.PluginScope;
import com.sshtools.forker.plugin.api.PluginSpec;
import com.sshtools.forker.plugin.api.ResolutionState;

public class DefaultPluginManager implements PluginManager {

	public final class DefaultPluginResolveContext extends PluginResolveContext {

		public DefaultPluginResolveContext() {
			super();
		}

		@Override
		public PluginDependencyTree getDependencyTree() {
			if (dependencyTree == null) {
				dependencyTree = new DefaultPluginDependencyTree(DefaultPluginManager.this,
						new DefaultClasspath(DefaultPluginManager.this));
			}
			return dependencyTree;
		}
	}

	final static String META_INF_PLUGINS = "META-INF/plugins";

	private static ThreadLocal<Stack<PluginResolveContext>> resolving = new ThreadLocal<>();

	private boolean autostart;
	private PluginClasspath classpath = new DefaultClasspath(this);
	private boolean cleanOrphans = true;
	private ConflictStrategy conflictStrategy = ConflictStrategy.ERROR;
	private PluginDependencyTree dependencyTree;
	private PluginEntryPointExecutor entryPointExecutor;
	private File local = new File(new File("tmp"), "plugin-repository");
	private List<String> pluginExclude = new ArrayList<>();
	private List<String> pluginInclude = new ArrayList<>();
	private Map<PluginComponentId, PluginInstance> plugins = new HashMap<>();
	private List<PluginRemote> remotes = new ArrayList<>();
	private boolean traces;
	private ResolutionState cachedState;
	private InstallMode installMode = InstallMode.AUTO;

	public DefaultPluginManager() {
	}

	public InstallMode getInstallMode() {
		return installMode;
	}

	public void setInstallMode(InstallMode installMode) {
		this.installMode = installMode;
	}

	@Override
	public void addRemote(PluginRemote remote) throws IOException {
		if (remotes.contains(remote))
			throw new IllegalArgumentException(String.format("Remote already %s added.", remote.getId()));
		remotes.add(remote);
		Collections.sort(remotes);
		remote.init(this);
	}

	@Override
	public void close() throws IOException {
		ProgressSink progress = new ProgressSink();
		while (plugins.size() > 0) {
			closePlugin(plugins.values().iterator().next().getComponentId().toString(), progress);
		}
	}

	@Override
	public void closePlugin(PluginSpec spec, PluginProgressMonitor progress) {
		PluginInstance inst = plugins.get(spec.getComponentId());
		if (inst == null)
			throw new IllegalArgumentException(String.format("Pluging %s is not started.", spec.getComponentId()));
		Class<?> resolvedClass = spec.getResolvedClass();
		try {
			for (Method m : resolvedClass.getMethods()) {
				for (PluginLifecycle a : m.getAnnotationsByType(PluginLifecycle.class)) {
					try {
						doLifecycleEvent(progress, PluginEvent.PRE_CLOSE, spec, m, a);
						for (PluginSpec child : getDependents(progress, spec)) {
							closePlugin(child, progress);
						}
						doLifecycleEvent(progress, PluginEvent.CLOSE, spec, m, a);
					} finally {
						doLifecycleEvent(progress, PluginEvent.POST_CLOSE, spec, m, a);
					}
				}
			}
		} finally {
			plugins.remove(spec.getComponentId());
			spec.dirtyState();
			if (inst.getThread() != null) {
				inst.getThread().interrupt();
			}

			/*
			 * If all of the plugins in the archive are stopped, unload all of the classes
			 * 
			 */
			int loaded = 0;
			for (PluginSpec siblingSpec : spec.getParent().getChildren()) {
				if (plugins.containsKey(siblingSpec.getComponentId())) {
					loaded++;
				}
			}
			if (loaded == 0) {
				spec.getParent().resetClassLoader();
			}
		}
	}

	@Override
	public void closePlugin(String componentId, PluginProgressMonitor monitor) {
		PluginComponentId cid = new PluginComponentId(componentId);
		PluginInstance inst = plugins.get(cid);
		if (inst == null)
			throw new IllegalArgumentException(String.format("Plugin %s is not started.", componentId));
		PluginSpec spec = getResolutionContext().getDependencyTree().get(cid, PluginSpec.class);
		if (spec == null)
			throw new IllegalArgumentException(String.format("Plugin %s not found.", componentId));
		closePlugin(spec, monitor);
	}

	@Override
	public URL downloadArchive(PluginProgressMonitor progress, PluginComponentId pa) throws IOException {
		if (remotes.isEmpty())
			throw new IOException(String.format("There are no remotes to download %s:%s:%s from.", pa.getGroup(),
					pa.getId(), pa.getVersion()));

		if (!local.exists() && !local.mkdirs())
			throw new IOException(String.format("Could not create local archives directory %s.", local));

		for (PluginRemote r : remotes) {
			if (r.isEnabled()) {
				URL d = r.retrieve(progress, pa);
				if (d != null) {
					progress.message(MessageType.INFO, String.format("Downloaded %s from %s", pa, d));
					return d;
				}
			}
		}
		return null;
	}

	@Override
	public Set<PluginArchive> getChildren() {
		if (!getState().isResolved())
			throw new IllegalStateException("Not resolved.");
		return getResolutionContext().getDependencyTree().getResolved();
	}

	@Override
	public ClassLoader getClassLoader() {
		return getClass().getClassLoader();
	}

	public PluginClasspath getClasspath() {
		return classpath;
	}

	@Override
	public PluginComponentId getComponentId() {
		return new PluginComponentId("@@@");
	}

	@Override
	public ConflictStrategy getConflictStrategy() {
		return conflictStrategy;
	}

	@Override
	public String getDescription() {
		return "Manages this applications plugins, providing core services for isolated class loading, installation and discovery.";
	}

	@Override
	public PluginEntryPointExecutor getEntryPointExecutor() {
		return entryPointExecutor;
	}

	@Override
	public Set<PluginArchive> getInstalled() throws IOException {
		if (!getState().isResolved())
			throw new IllegalStateException("Not resolved.");
		Set<PluginArchive> installed = new LinkedHashSet<>();
		for (PluginArchive arc : getResolutionContext().getDependencyTree().getResolved()) {
			if (arc.getState().isResolved() && arc.getArchive() != null
					&& arc.getArchive().getProtocol().equals("file")) {
				File file = new File(arc.getArchive().getPath());
				if (file.getParentFile().equals(getLocal().getAbsoluteFile())) {
					installed.add(arc);
				}
			}
		}
		return installed;
	}

	@Override
	public File getLocal() {
		return local;
	}

	@Override
	public String getName() {
		return "Plugin Manager";
	}

	@Override
	public PluginComponent<?> getParent() {
		return null;
	}

	public List<String> getPluginExclude() {
		return pluginExclude;
	}

	public List<String> getPluginInclude() {
		return pluginInclude;
	}

	@Override
	public Set<PluginSpec> getPlugins() {
		Set<PluginSpec> l = new LinkedHashSet<>();
		l.addAll(getStoppedPlugins());
		l.addAll(getStartedPlugins());
		return l;
	}

	@Override
	public List<PluginRemote> getRemotes() {
		return Collections.unmodifiableList(remotes);
	}

	@Override
	public PluginResolveContext getResolutionContext() {
		Stack<PluginResolveContext> stack = resolving.get();
		if (stack == null || stack.size() == 0)
			return new DefaultPluginResolveContext();
		else
			return stack.peek();
	}

	@Override
	public Set<PluginSpec> getStartedPlugins() {
		if (!getState().isResolved())
			throw new IllegalStateException("Not resolved.");
		Set<PluginSpec> l = new LinkedHashSet<>();
		for (PluginInstance inst : plugins.values())
			l.add(getResolutionContext().getDependencyTree().get(inst.getComponentId(), PluginSpec.class));
		return l;
	}

	@Override
	public ResolutionState getState() {
		if (cachedState != null)
			return cachedState;
		ResolutionState state = ResolutionState.UNRESOLVED;
		for (PluginArchive child : getResolutionContext().getDependencyTree().getResolved()) {
			if (!child.getState().isResolved()) {
				if (child.getComponentId().isOptional())
					state = ResolutionState.SATISFACTORY;
				else {
					state = ResolutionState.PARTIAL;
					break;
				}
			} else if (state == ResolutionState.UNRESOLVED)
				state = ResolutionState.RESOLVED;
		}
		if (state.isResolved()) {
			/* Only worth checking for plugins if we have found any archives! */
			for (PluginSpec spec : getResolutionContext().getDependencyTree().getUnresolvedPlugins()) {
				if (spec.getComponentId().isOptional() && state != ResolutionState.PARTIAL) {
					state = ResolutionState.SATISFACTORY;
				} else
					state = ResolutionState.PARTIAL;
			}
		}

		return cachedState = state;
	}

	@Override
	public Set<PluginSpec> getStoppedPlugins() {
		if (!getState().isResolved())
			throw new IllegalStateException("Not resolved.");
		Set<PluginSpec> l = new LinkedHashSet<>();
		for (PluginArchive arch : getResolutionContext().getDependencyTree().getResolved()) {
			for (PluginSpec spec : arch.getChildren()) {
				if (!plugins.containsKey(spec.getComponentId()))
					l.add(spec);
			}
		}
		return l;
	}

	@Override
	public Set<PluginArchive> getUnresolvedArchives() throws IOException {
		Set<PluginArchive> r = new LinkedHashSet<>();
		for (PluginArchive child : getChildren()) {
			if (!child.getState().isResolved())
				r.add(child);
		}
		return r;
	}

	@Override
	public Set<PluginSpec> getUnresolvedPlugins() {
		if (!getState().isResolved())
			throw new IllegalStateException("Not resolved.");
		return getResolutionContext().getDependencyTree().getUnresolvedPlugins();
	}

	@Override
	public void install(PluginProgressMonitor progress, boolean optional, boolean failOnError, boolean startAutostarts,
			boolean forceStart, PluginComponentId... ids) throws IOException {
		execTaskInResolveContext(progress, new DefaultPluginResolveContext().failOnError(failOnError)
				.resolveOptional(optional).startAutostartlugins(false).forceStartPlugins(false), (p, resolve) -> {
					for (PluginComponentId id : ids) {
						PluginArchive existing = getResolutionContext().getDependencyTree().get(id.idAndGroup(),
								PluginArchive.class);
						if (existing != null && existing.getArchive() != null) {

							/* Version differs */
							DefaultArtifactVersion availableVersion = new DefaultArtifactVersion(id.getVersion());
							DefaultArtifactVersion installedVersion = new DefaultArtifactVersion(
									existing.getComponentId().getVersion());
							if (availableVersion.compareTo(installedVersion) < 0) {
								throw new IOException(String.format(
										"%s cannot downgrade from version %s to version %s when installing. ", id,
										existing.getComponentId(), installedVersion, availableVersion));
							}

						}
					}
					List<PluginComponentId> idList = new LinkedList<>(Arrays.asList(ids));
					progress.start(remotes.size());
					try {
						int r = 0;
						for (PluginRemote remote : remotes) {
							if (idList.isEmpty())
								break;
							if (remote.isEnabled()) {
								progress.progress(++r, String.format("Querying %s", remote.getId()));
								for (int i = idList.size() - 1; i >= 0; i--) {
									PluginComponentId id = idList.get(i);
									URL url = remote.retrieve(progress, id);
									if (url != null) {
										idList.remove(i);
									}
								}
							}
						}
					} finally {
						progress.end();
					}
					if (!idList.isEmpty())
						throw new IOException(
								"Failed to install at least one component. " + PluginComponentId.toString(idList));

					return null;
				});

		resolve(progress, optional, failOnError, true, startAutostarts, forceStart);
	}

	@Override
	public void install(PluginProgressMonitor progress, boolean forceStart, PluginComponentId... ids)
			throws IOException {
		install(progress, false, true, autostart, false, ids);
	}

	@Override
	public boolean isAutostart() {
		return autostart;
	}

	@Override
	public boolean isCleanOrphans() {
		return cleanOrphans;
	}

	@Override
	public boolean isStarted() {
		return plugins.size() > 0;
	}

	@Override
	public boolean isStarted(PluginComponentId spec) {
		return plugins.containsKey(spec);
	}

	public boolean isTraces() {
		return traces;
	}

	@Override
	public Set<PluginArchive> list(PluginProgressMonitor monitor) throws IOException {
		DefaultPluginDependencyTree tempTree = new DefaultPluginDependencyTree(DefaultPluginManager.this);
		return execTaskInResolveContext(monitor, new PluginResolveContext() {
			@Override
			public DefaultPluginDependencyTree getDependencyTree() {
				return tempTree;
			}
		}.failOnError(false).resolvePlugins(false).conflictStrategy(
				conflictStrategy == ConflictStrategy.ERROR ? ConflictStrategy.USE_BOTH : conflictStrategy),
				(p, resolve) -> {
					monitor.start(remotes.size());
					try {
						int idx = 0;
						for (PluginRemote remote : remotes) {
							monitor.progress(++idx, remote.getId());
							try {
								if (remote.isEnabled()) {
									for (PluginArchive arc : remote.list(monitor)) {
										resolve.getDependencyTree().add(arc, monitor);
									}
								}
							} catch (FileNotFoundException fne) {
								monitor.message(MessageType.WARNING,
										String.format("Failed to query remote %s", remote.getId()));
							}
						}
						return resolve.getDependencyTree().resolve(monitor, false);
					} finally {
						monitor.end();
					}
				});

	}

	@Override
	public Set<PluginArchive> list(PluginRemote remote, PluginProgressMonitor monitor) throws IOException {
		DefaultPluginDependencyTree tempTree = new DefaultPluginDependencyTree(DefaultPluginManager.this);
		return execTaskInResolveContext(monitor, new PluginResolveContext() {
			@Override
			public DefaultPluginDependencyTree getDependencyTree() {
				return tempTree;
			}
		}.failOnError(false).resolvePlugins(false).conflictStrategy(
				conflictStrategy == ConflictStrategy.ERROR ? ConflictStrategy.USE_BOTH : conflictStrategy),
				(p, resolve) -> {
					monitor.start(remotes.size());
					try {
						int idx = 0;
						monitor.progress(++idx, remote.getId());
						try {
							if (remote.isEnabled()) {
								for (PluginArchive arc : remote.list(monitor)) {
									resolve.getDependencyTree().add(arc, monitor);
								}
							}
						} catch (FileNotFoundException fne) {
							monitor.message(MessageType.WARNING,
									String.format("Failed to query remote %s", remote.getId()));
						}
						return resolve.getDependencyTree().resolve(monitor, false);
					} finally {
						monitor.end();
					}
				});

	}

	@Override
	public void removeRemote(PluginRemote remote) throws IOException {
		if (!remotes.contains(remote))
			throw new IllegalArgumentException(String.format("Remote already %s added.", remote.getId()));
		remotes.remove(remote);
		remote.close();
	}

	@Override
	public void resolve(PluginProgressMonitor progress) throws IOException {
		resolve(progress, false, false, false, isAutostart(), false);
	}

	@Override
	public void resolve(PluginProgressMonitor progress, boolean optional, boolean failOnError, boolean download,
			boolean startAutostarts, boolean forceStart) throws IOException {
		doResolve(new DefaultPluginResolveContext().failOnError(failOnError).resolveOptional(optional)
				.download(download).startAutostartlugins(startAutostarts), progress);
	}

	@Override
	public void setAutostart(boolean autoStart) {
		this.autostart = autoStart;
	}

	public void setClasspath(PluginClasspath classpath) {
		this.classpath = classpath;
	}

	@Override
	public void setCleanOrphans(boolean cleanOrphans) {
		this.cleanOrphans = cleanOrphans;
	}

	@Override
	public void setConflictStrategy(ConflictStrategy conflictStrategy) {
		this.conflictStrategy = conflictStrategy;
	}

	@Override
	public void setEntryPointExecutor(PluginEntryPointExecutor entryPointExecutor) {
		this.entryPointExecutor = entryPointExecutor;
	}

	@Override
	public void setLocal(File local) {
		this.local = local;
	}

	public void setTraces(boolean traces) {
		this.traces = traces;
	}

	@Override
	public void start(PluginComponentId id, PluginProgressMonitor progress, String... args) throws IOException {
		if (!id.hasPlugin())
			throw new IllegalArgumentException(
					"May only start plugins. The ID supplied did not contain the plugin class name.");
		List<PluginSpec> plugins = execTaskInResolveContext(progress, (p, resolve) -> {
			List<PluginSpec> l = new LinkedList<>();
			for (PluginArchive archive : getResolutionContext().getDependencyTree().getResolved())
				l.addAll(archive.getChildren());
			return l;
		});

		PluginSpec match = bestMatch(plugins, id);
		if (match != null) {
			start(match, progress, args);
			return;
		}
		throw new IllegalArgumentException(String.format("No plugin %s", id));
	}

	@Override
	public void start(PluginProgressMonitor progress, String... args) throws IOException {
		execTaskInResolveContext(progress, (p, resolve) -> {
			for (PluginArchive archive : getResolutionContext().getDependencyTree().getResolved()) {
				ResolutionState state = archive.getState();
				if (state.isUsable()) {
					for (PluginSpec spec : archive.getChildren()) {
						if (!plugins.containsKey(spec.getComponentId()))
							doStartPlugin(spec, p, args);
					}
				}
			}
			return null;
		});

	}

	@Override
	public void start(PluginSpec plugin, PluginProgressMonitor progress, String... args) throws IOException {
		if (plugins.containsKey(plugin.getComponentId()))
			throw new IllegalStateException(String.format("%s already started.", plugin.getComponentId()));
		doStartPlugin(plugin, progress, args);
	}

	@Override
	public void start(String id, PluginProgressMonitor progress, String... args) throws IOException {
		start(new PluginComponentId(id), progress, args);
	}

	@Override
	public void uninstall(PluginProgressMonitor progress, PluginComponentId... ids) throws IOException {
		execTaskInResolveContext(progress, (p, resolve) -> {
			for (PluginComponentId id : ids) {
				checkCanUninstall(progress, id);
			}
			for (PluginComponentId id : ids) {
				doUninstall(progress, id);
			}
			doResolve(new DefaultPluginResolveContext().failOnError(false).resolveOptional(false), progress);
			return null;
		});
	}

	protected <T extends PluginComponent<?>> T bestMatch(Collection<T> comps, PluginComponentId id) {
		T possible = null;
		T actual = null;
		for (T t : comps) {
			if (id.onlyHasPlugin() && id.getPlugin().equals(t.getComponentId().getPlugin())) {
				possible = t;
			} else if (matches(id, t)) {
				actual = t;
				break;
			}
		}
		if (actual == null)
			actual = possible;
		return actual;
	}

	protected void checkCanUninstall(PluginProgressMonitor progress, PluginComponentId id) throws IOException {
		if (id.hasPlugin())
			throw new IllegalArgumentException(
					String.format("Cannot uninstall %s, it is a plugin. Uninstall it's parent archive instead, %s", id,
							id.withoutPlugin()));

		/* Does it exist? */
		PluginArchive arc = getResolutionContext().getDependencyTree().get(id, PluginArchive.class);
		if (arc == null) {
			throw new IllegalArgumentException(String.format("%s is not installed.", id));
		}

		/* Is it a local archive ? */
		if (arc.getArchive() == null || !arc.getArchive().getProtocol().equals("file")) {
			throw new IllegalArgumentException(
					String.format("%s is not a local archive, and so cannot be removed.", id));
		}

		/* Is it in the local repository */
		File file = new File(arc.getArchive().getPath());
		if (!file.getParentFile().getAbsoluteFile().equals(getLocal().getAbsoluteFile())) {
			throw new IllegalArgumentException(String.format(
					"%s is not installed in the local repository at %s, and so cannot be removed.", id, getLocal()));
		}

		/* Are all of the plugins in this archive stopped? */
		for (PluginSpec spec : arc.getChildren()) {
			if (plugins.containsKey(spec.getComponentId())) {
				throw new IllegalArgumentException(String.format(
						"%s cannot be uninstalled as the plugin %s is running which depends on it. Stop the plugin first.",
						id, spec.getComponentId()));
			}
		}

		/* Now check the same things on everything that is dependent on this archive. */
		for (PluginComponentId dep : arc.getDependents()) {
			if (!dep.isOptional())
				checkCanUninstall(progress, dep);
		}
	}

	protected void doResolve(PluginResolveContext ctx, PluginProgressMonitor monitor) throws IOException {
		dependencyTree = null;
		execTaskInResolveContext(monitor, ctx, (p, resolve) -> {
			resolve.getDependencyTree().resolve(monitor, true);

			Map<URL, PluginArchive> archives = new HashMap<>();
			for (PluginArchive child : getChildren()) {
				archives.put(child.getArchive(), child);
			}

			if (cleanOrphans) {
				cleanOrphans(monitor);
			}
			return null;
		});
	}

	public void cleanOrphans(PluginProgressMonitor progress) {
		if (getLocal().exists()) {
			Set<PluginArchive> children = new LinkedHashSet<>(getChildren());
			if (!children.isEmpty()) {
				progress.start(children.size());
				int p = 0;
				try {
					for (PluginArchive archive : children) {
						progress.progress(++p, archive.getComponentId().toString());
						if (archive.getScope() == PluginScope.INSTALLED) {
							if (archive.getChildren().isEmpty() && archive.getDependents().isEmpty()) {

								progress.message(MessageType.INFO, String
										.format("Removing %s, it is no longer refrenced.", archive.getComponentId()));
								try {
									uninstall(progress, archive.getComponentId());
								} catch (IOException e) {
									progress.message(MessageType.ERROR,
											String.format("Failed to clean up %s", archive.getComponentId()));
									if (traces)
										e.printStackTrace();
								}
							}
						}
					}
				} finally {
					progress.end();
				}
			}
		}
	}

	protected void doStartPlugin(PluginSpec spec, PluginProgressMonitor progress, String[] args) throws IOException {
		if (plugins.containsKey(spec.getComponentId())) {
			return;
		}

		progress.start(3);
		String[] wasArgs = getResolutionContext().arguments();
		try {
			getResolutionContext().arguments(args);

			/*
			 * Make sure the plugin is full resolved (i.e. with it's class and dependencies
			 */
			progress.progress(1, String.format("Resolving plugin %s", spec.getComponentId()));
			if (!spec.getState().isResolved()) {
				spec.resolve(progress);
			}

			/* Start dependencies first */
			progress.progress(2, "Starting dependencies");
			for (PluginComponentId dep : spec.getDependencies()) {
				PluginSpec depSpec = getResolutionContext().getDependencyTree().get(dep, PluginSpec.class);
				if (depSpec == null) {
					if (dep.isOptional())
						progress.message(MessageType.INFO,
								String.format("Optional dependency %s does not exist, skipping", dep));
					else
						throw new IllegalStateException(String.format("Plugin dependency %s (of %s) does not exist",
								dep, spec.getComponentId()));
				} else
					doStartPlugin(depSpec, progress, args);
			}

			Class<?> resolvedClass = spec.getResolvedClass();
			progress.progress(3, "Starting plugin");
			progress.message(MessageType.INFO, String.format("Starting plugin %s", spec.getComponentId()));
			PluginInstance instance = createPlugin(progress, spec);
			plugins.put(spec.getComponentId(), instance);
			spec.dirtyState();

			for (Method m : resolvedClass.getMethods()) {
				PluginEntryPoint pep = m.getAnnotation(PluginEntryPoint.class);
				if (pep != null) {
					if (!Modifier.isStatic(m.getModifiers())) {
						throw new IllegalStateException(
								String.format("The plugin %s contains a %s, but the method is not static.",
										spec.getComponentId(), PluginEntryPoint.class));
					}
					if (entryPointExecutor == null)
						throw new IllegalStateException(String.format(
								"The plugin %s contains a %s. This means a %s must be registered with the %s",
								spec.getComponentId(), PluginEntryPoint.class, PluginEntryPointExecutor.class,
								PluginManager.class));
					else
						try {
							entryPointExecutor.exec(() -> {
								try {
									instance.setThread(Thread.currentThread());
									ClassLoader was = Thread.currentThread().getContextClassLoader();
									try {
										Thread.currentThread()
												.setContextClassLoader(instance.getClass().getClassLoader());
										m.invoke(null, (Object) args);
									} finally {
										Thread.currentThread().setContextClassLoader(was);
									}
								} catch (InvocationTargetException e) {
									if (!(e.getCause() instanceof InterruptedException)) {
										spec.setError(e.getCause());
										throw new IllegalStateException("Failed to execute.", e.getCause());
									} else if (e.getCause() instanceof RuntimeException) {
										spec.setError(e.getCause());
										throw (RuntimeException) e.getCause();
									}
								} catch (Exception e) {
									spec.setError(e);
									throw new IllegalStateException("Failed to execute.", e);
								} finally {
									/* Set the thread to null to prevent it trying to be interrupted again */
									instance.setThread(null);
									try {
										closePlugin(spec, new ProgressSink());
									} catch (IllegalArgumentException iae) {
										// May have been closed externally
										spec.dirtyState();
									}
								}
							});
						} catch (InterruptedException e) {
							throw new IllegalStateException("Interrupted queueing on the entry point thread.");
						}
				}
			}

			for (Method m : resolvedClass.getMethods()) {
				for (PluginLifecycle a : m.getAnnotationsByType(PluginLifecycle.class)) {
					try {
						doLifecycleEvent(progress, PluginEvent.PRE_START, spec, m, a);
						doLifecycleEvent(progress, PluginEvent.START, spec, m, a);
					} finally {
						doLifecycleEvent(progress, PluginEvent.POST_START, spec, m, a);
					}
				}
			}
		} finally

		{
			progress.end();
			getResolutionContext().arguments(wasArgs);
		}
	}

	protected void doUninstall(PluginProgressMonitor progress, PluginComponentId id) throws IOException {
		PluginArchive arc = getResolutionContext().getDependencyTree().get(id, PluginArchive.class);
		if (!arc.getComponentId().isOptional()) {
			for (PluginComponentId dep : arc.getDependents()) {
				doUninstall(progress, dep);
			}
		}
		File file = new File(arc.getArchive().getPath());
		if (file.exists())
			if (file.isDirectory())
				FileUtils.deleteDirectory(file);
			else if (!file.delete())
				throw new IOException(String.format("Failed to delete %s.", file));

		// TODO should the remote do this?
		File pom = new File(file.getParentFile(), id + ".pom");
		if (pom.exists() && !pom.delete())
			throw new IOException(String.format("Failed to delete %s.", pom));
	}

	protected <C> C execTaskInResolveContext(PluginProgressMonitor monitor, PluginResolveContext context,
			PluginResolveTask<C> task) throws IOException {
		if (resolving.get() != null) {
			resolving.get().push(context);
			try {
				return task.exec(monitor, context);
			} finally {
				resolving.get().pop();
			}
		} else {
			resolving.set(new Stack<>());
			resolving.get().push(context);
			try {
				return task.exec(monitor, context);
			} finally {
				resolving.remove();
			}
		}
	}

	protected <C> C execTaskInResolveContext(PluginProgressMonitor monitor, PluginResolveTask<C> task)
			throws IOException {
		return execTaskInResolveContext(monitor, new DefaultPluginResolveContext(), task);
	}

	protected Collection<PluginSpec> getDependents(PluginProgressMonitor progress, PluginSpec spec) {
		List<PluginSpec> deps = new ArrayList<>();
		for (PluginInstance inst : plugins.values()) {
			PluginSpec depSpec = getResolutionContext().getDependencyTree().get(inst.getComponentId(),
					PluginSpec.class);
			if (depSpec != null && depSpec.getDependencies().contains(spec.getComponentId())) {
				deps.add(depSpec);
			}
		}
		return deps;
	}

	protected boolean matches(PluginComponentId id, PluginComponent<?> component) {
		if (id.hasVersion()) {
			return component.getComponentId().equals(id);
		} else {
			return component.getComponentId().withoutVersion().equals(id);
		}
	}

	private PluginInstance createPlugin(PluginProgressMonitor monitor, PluginSpec spec) {
		if (plugins.containsKey(spec.getComponentId()))
			return plugins.get(spec.getComponentId());
		else {
			PluginInstance instance;
			try {
				if (spec.isStatic())
					instance = new PluginInstance(null, spec.getComponentId());
				else
					instance = new PluginInstance(spec.getResolvedClass().newInstance(), spec.getComponentId());
			} catch (InstantiationException | IllegalAccessException e) {
				throw new RuntimeException("Failed to create plugin instance.", e);
			}

			for (Field f : spec.getResolvedClass().getDeclaredFields()) {
				Manager mgr = f.getAnnotation(Manager.class);
				if (mgr != null) {
					try {
						f.setAccessible(true);
						f.set(instance.getInstance(), this);
					} catch (IllegalArgumentException | IllegalAccessException e) {
						monitor.message(MessageType.ERROR, String.format("Could not inject %s instance into %s.",
								PluginManager.class, spec.getComponentId()));
					}
				}
			}

			plugins.put(spec.getComponentId(), instance);
			return instance;
		}
	}

	private void doLifecycleEvent(PluginProgressMonitor monitor, PluginEvent evt, PluginSpec spec, Method m,
			PluginLifecycle a) {
		if (a.event() == evt) {

			ClassLoader was = Thread.currentThread().getContextClassLoader();
			try {
				Thread.currentThread().setContextClassLoader(spec.getClassLoader());

				PluginInstance plugin = createPlugin(monitor, spec);
				if (plugin != null) {
					try {
						if (m.getParameterTypes().length == 1 && m.getParameterTypes()[0].equals(String[].class)) {
							String[] argarr = getResolutionContext().arguments();
							m.invoke(plugin.getInstance(), (Object) argarr);
						} else
							m.invoke(plugin.getInstance());
					} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
						spec.setError(e.getCause());
						if (monitor != null)
							monitor.message(MessageType.ERROR, String.format(
									"Failed to execute lifecycle event on plugin %s. %s", spec.getComponentId(),
									e.getMessage() == null ? (e.getCause() == null || e.getCause().getMessage() == null
											? "No further information."
											: e.getCause().getMessage()) : e.getMessage()));
					}
				}
			} finally {
				Thread.currentThread().setContextClassLoader(was);
			}

		}
	}

	@Override
	public PluginScope getScope() {
		return PluginScope.SYSTEM;
	}

	@Override
	public void dirtyState() {
		cachedState = null;
	}

	@Override
	public boolean addChild(PluginArchive... child) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean removeChild(PluginArchive... child) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int compareTo(PluginComponent<?> o) {
		// Meaningless!
		return toString().compareTo(o.toString());
	}
}
