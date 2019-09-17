package com.sshtools.forker.plugin;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;

import com.sshtools.forker.plugin.api.Plugin;
import com.sshtools.forker.plugin.api.PluginArchive;
import com.sshtools.forker.plugin.api.PluginComponentId;
import com.sshtools.forker.plugin.api.PluginDependencyTree;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;
import com.sshtools.forker.plugin.api.PluginScope;
import com.sshtools.forker.plugin.api.PluginSpec;
import com.sshtools.forker.plugin.api.ResolutionState;

public class DefaultPluginSpec extends AbstractNode<PluginArchive> implements PluginSpec {

	private PluginArchive archive;
	private boolean autostart;
	private String[] dependencyNames;
	private Throwable error;
	private boolean resetClassLoader;
	private Class<?> resolvedClass;
	private boolean resolvedDependencies;
	private boolean staticPlugin;

	public DefaultPluginSpec(PluginArchive archive, PluginComponentId id, String[] dependencyNames, String name,
			String description) {
		super(archive.getParent(), id);
		this.archive = archive;
		this.dependencyNames = dependencyNames;
		setName(name);
		setDescription(description);
	}

	public DefaultPluginSpec(PluginArchive archive, String classSpec, String[] dependencyNames, String name,
			String description) {
		super(archive.getParent(), archive.getComponentId().withPlugin(classSpec));
		this.archive = archive;
		this.dependencyNames = dependencyNames;
		setName(name);
		setDescription(description);
	}

	@Override
	public PluginScope getScope() {
		return getParent().getScope();
	}

	@Override
	public ClassLoader getClassLoader() {
		if (getParent() == null)
			throw new IllegalStateException(
					String.format("Plugin %s has no parent, and so no classloader", getComponentId()));
		return getParent().getClassLoader();
	}

	public Throwable getError() {
		return error;
	}

	protected void onNodeDirtyState() {
		error = null;
	}

	public PluginArchive getParent() {
		return archive;
	}

	@Override
	public Class<?> getResolvedClass() {
		if (resetClassLoader) {
			resolveClassState(new ProgressSink());
		}
		if (resolvedClass == null) {
			throw new IllegalStateException(
					String.format("%s it not a resolved plugin class.", getComponentId().getPlugin()));
		}
		return resolvedClass;
	}

	@Override
	protected ResolutionState calcState() {
		if (error != null)
			return ResolutionState.ERRORED;
		if (getManager().isStarted(getComponentId()))
			return ResolutionState.STARTED;
		ResolutionState base = calcBaseState();
		if (base.isResolved()) {
			if (base.isResolved())
				base = calcClassState();
			if (base.isResolved())
				base = calcPluginDependencyState();
			if (base.isResolved())
				base = calcNodeState();
		}
		return base;
	}

	@Override
	public boolean isAutostart() {
		return autostart;
	}

	public boolean isStatic() {
		return staticPlugin;
	}

	@Override
	public void resetClassLoader() {
		/*
		 * When the archives class loader is reset, we remove the resolved plugin class
		 * (i.e. the one with the annotation, but still leave the plugin in a RESOLVED
		 * state. When the class is needed again (i.e. for startup), it will be lazily
		 * loaded again.
		 */
		resetClassLoader = true;
		resolvedClass = null;
		dirtyState();
	}

	@Override
	protected void doResolve(PluginProgressMonitor progress) throws IOException {
		error = null;
		ResolutionState base = calcBaseState();
		if (base.isResolved()) {
			if (!calcClassState().isResolved())
				resolveClassState(progress);

			if (!calcPluginDependencyState().isResolved())
				resolvePluginDependencies(progress);

			if (!calcNodeState().isResolved())
				resolveNode(progress);
		}

	}

	@Override
	public void setAutostart(boolean autostart) {
		this.autostart = autostart;
	}

	public void setError(Throwable error) {
		this.error = error;
	}

	public void setStatic(boolean staticPlugin) {
		this.staticPlugin = staticPlugin;
	}

	@Override
	public String toString() {
		return "PluginSpec [spec=" + getComponentId() + "(" + getComponentId().getType() + "), archive="
				+ (archive == null ? "" : archive.getComponentId()) + ", dependencies="
				+ PluginComponentId.toString(getDependencies()) + ", resolvedClass=" + resolvedClass + "]";
	}

	protected ResolutionState calcClassState() {
		return resolvedClass == null && !resetClassLoader ? ResolutionState.UNRESOLVED : ResolutionState.RESOLVED;
	}

	protected ResolutionState calcPluginDependencyState() {
		return resolvedDependencies ? ResolutionState.RESOLVED : ResolutionState.UNRESOLVED;
	}

	protected void resolveClassState(PluginProgressMonitor progress) {
		try {
			this.resolvedClass = getClassLoader().loadClass(getComponentId().getPlugin());
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException(String.format("Could not resolve %s in %s.", getComponentId().getPlugin(),
					getComponentId().withoutPlugin()));
		} finally {
			resetClassLoader = false;
		}
	}

	protected void resolvePluginDependencies(PluginProgressMonitor monitor) throws IOException {
		if (!resolvedDependencies) {
			PluginDependencyTree depTree = getManager().getResolutionContext().getDependencyTree();

			if (dependencyNames == null) {
				Class<?> clazz = getResolvedClass();
				Plugin plugin = clazz.getAnnotation(Plugin.class);
				if (plugin != null) {
					dependencyNames = plugin.dependencies();
					if (StringUtils.isNotBlank(plugin.name()))
						setName(plugin.name());
					if (StringUtils.isNotBlank(plugin.description()))
						setDescription(plugin.description());
					setStatic(plugin.staticLoad());
				}
			}

			if (dependencyNames != null) {
				for (String dep : dependencyNames) {

					PluginComponentId thisId = getComponentId();
					PluginArchive parent = getParent();

					if (!thisId.hasVersion() && parent != null && parent.getComponentId().hasVersion())
						thisId = thisId.withVersion(parent.getComponentId().getVersion());

					PluginComponentId depId = PluginComponentId.resolvePluginId(thisId, dep);

					/*
					 * If the dependency doesn't declare a version, first check to see if there is a
					 * newer archive than this one. If there is, it likely has the plugins we want,
					 * so we will add that one instead
					 */
					if (!depId.hasVersion()) {
						PluginArchive arc = depTree.newest(depId.idAndGroup(), PluginArchive.class);
						if (arc != parent) {
							parent = arc;
						}
					}

					/*
					 * Look for an existing plugin, first with any version, then with the same
					 * version as this plugin
					 */
					PluginSpec depSpec = depTree.get(depId.withoutVersion(), PluginSpec.class);
					if (depSpec == null && !depId.hasVersion() && parent.getComponentId().hasVersion()) {
						depId = depId.withVersion(parent.getComponentId().getVersion());
						depSpec = depTree.get(depId, PluginSpec.class);
					}

					addDependency(depId);

					if (depSpec == null) {
						/*
						 * This is the first time the plugin has been encountered, create it. If the
						 * plugin appears to be in the same archive as this one, we add it to that
						 * archive.
						 * 
						 * If it is from a different archive, then look to see if we already have it. If
						 * so, add this plugin to that archive. If not, resolve that artifact in the
						 * next parse (exiting immediately from here so this gets called again when it
						 * is resolved)
						 */
						if (parent != null && parent.getComponentId().idAndGroup().equals(depId.idAndGroup())) {
							/*
							 * In the same archive, add the plugin to it, it will get fully resolved on the
							 * next pass.
							 */
							depSpec = new DefaultPluginSpec(parent, depId, null, null, null);
							parent.addChild(depSpec);
//							getParent().addDependency(parent.getComponentId());
//							parent.addDependent(getParent().getComponentId());

						} else {
							/*
							 * In a different archive. Do we have that archive? If so, use it, otherwise
							 * resolve it.
							 */
							PluginArchive depArc = depTree.get(depId.withoutPlugin(), PluginArchive.class);
							if (depArc == null) {
								/* Archive not yet resolved, create it queue it for resolution. */
								depArc = new DefaultArchive(getManager(), null, depId.withoutPlugin());
								depSpec = new DefaultPluginSpec(depArc, depId, null, null, null);
								depArc.addChild(depSpec);
								depArc.addDependent(parent.getComponentId());
								parent.addDependency(depArc.getComponentId());
								depTree.add(depArc, monitor);
							} else {
								/*
								 * Archive is already resolved. Add the plugin. In theory I don't think we will
								 * ever get here as the plugin should have already been found!
								 */
								depSpec = new DefaultPluginSpec(depArc, depId, null, null, null);
								depArc.addChild(depSpec);

								depArc.addDependent(parent.getComponentId());
								parent.addDependency(depArc.getComponentId());
							}
						}
					}
					depSpec.addDependent(thisId);
				}
			}
			resolvedDependencies = true;
		}
	}

	@Override
	public void reparent(PluginArchive archive) {
		this.archive = archive;
		resetClassLoader();
	}

}
