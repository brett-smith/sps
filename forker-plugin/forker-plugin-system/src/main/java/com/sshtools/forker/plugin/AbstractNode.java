package com.sshtools.forker.plugin;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import com.sshtools.forker.plugin.api.PluginArchive;
import com.sshtools.forker.plugin.api.PluginComponent;
import com.sshtools.forker.plugin.api.PluginComponentId;
import com.sshtools.forker.plugin.api.PluginDependencyTree;
import com.sshtools.forker.plugin.api.PluginManager;
import com.sshtools.forker.plugin.api.PluginNode;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;
import com.sshtools.forker.plugin.api.PluginProgressMonitor.MessageType;
import com.sshtools.forker.plugin.api.ResolutionState;

public abstract class AbstractNode<P extends PluginComponent<?>> extends AbstractComponent<P> implements PluginNode<P> {

	private Set<PluginComponentId> dependants = new LinkedHashSet<>();
	private final Set<PluginComponentId> dependencies = new LinkedHashSet<>();
	private PluginManager manager;

	public AbstractNode(PluginManager manager, PluginComponentId component) {
		super(component);
		this.manager = manager;
	}

	@Override
	public Set<PluginComponentId> getAllDependencies() {
		return collectDependencies(getManager().getResolutionContext().getDependencyTree(), this, new HashSet<>());
	}

	@Override
	public Set<PluginComponentId> getAllDependents() {
		return collectDependents(getManager().getResolutionContext().getDependencyTree(), this, new HashSet<>());
	}

	@Override
	public final void addDependent(PluginComponentId... dep) {
		boolean added = false;
		Set<PluginComponentId> allDeps = collectDependents(getManager().getResolutionContext().getDependencyTree(),
				this, new HashSet<>());
		for (PluginComponentId id : dep) {
			if (!allDeps.contains(id)) {
				if (id.equals(getComponentId()))
					throw new IllegalArgumentException(String.format("%s cannot depend on itself.", id));
				added = dependants.add(id) || added;
			}
		}
		if (added)
			dirtyState();
	}

	public final static Set<PluginComponentId> collectDependencies(PluginDependencyTree tree, PluginNode<?> archive,
			Set<PluginComponentId> l) {
		for (PluginComponentId id : archive.getDependencies()) {
			PluginNode<?> dep = tree.get(id, PluginNode.class);
			if (dep != null) {
				if (!l.contains(id)) {
					l.add(id);
					collectDependencies(tree, dep, l);
				}
			}
		}
		return l;
	}

	public final static Set<PluginComponentId> collectDependents(PluginDependencyTree tree, PluginNode<?> archive,
			Set<PluginComponentId> l) {
		for (PluginComponentId id : archive.getDependents()) {
			PluginArchive dep = tree.get(id, PluginArchive.class);
			if (dep != null) {
				if (!l.contains(id)) {
					l.add(id);
					collectDependents(tree, dep, l);
				}
			}
		}
		return l;
	}

	@Override
	public final void removeDependent(PluginComponentId... dep) {
		boolean removed = false;
		for (PluginComponentId id : dep) {
			removed = dependants.remove(id) || removed;
		}
		if (removed)
			dirtyState();
	}

	@Override
	public final void addDependency(PluginComponentId... dep) {
		boolean added = false;
		Set<PluginComponentId> allDeps = collectDependencies(getManager().getResolutionContext().getDependencyTree(),
				this, new HashSet<>());
		for (PluginComponentId id : dep) {
			if (!allDeps.contains(id)) {
				if (id.equals(getComponentId()))
					throw new IllegalArgumentException(String.format("%s cannot depend on itself.", id));
				added = dependencies.add(id) || added;
			}
		}
		if (added) {
			dirtyState();
		}
	}

	@Override
	public final void removeDependency(PluginComponentId... dep) {
		boolean removed = false;
		for (PluginComponentId id : dep) {
			removed = dependencies.remove(id) || removed;
		}
		if (removed) {
			dirtyState();
		}
	}

	public final Set<PluginComponentId> getDependencies() {
		return Collections.unmodifiableSet(dependencies);
	}

	public final Set<PluginComponentId> getDependents() {
		return Collections.unmodifiableSet(dependants);
	}

	protected final void onDirtyState() {
		for (PluginComponentId cid : getDependents()) {
			PluginComponent<?> pc = manager.getResolutionContext().getDependencyTree().get(cid, PluginComponent.class);
			if (pc != null)
				pc.dirtyState();
		}
		onNodeDirtyState();
	}

	protected void onNodeDirtyState() {
	}

	@Override
	protected ResolutionState calcState() {
		ResolutionState base = calcBaseState();
		if (base.isResolved()) {
			base = calcNodeState();
		}
		return base;
	}

	@Override
	protected void doResolve(PluginProgressMonitor progress) throws IOException {
		if (!calcNodeState().isComplete())
			resolveNode(progress);

	}

	protected final ResolutionState calcNodeState() {
		ResolutionState thisState = ResolutionState.RESOLVED;
		/* Now check the archive dependencies in the same way */
		Set<PluginComponentId> deps = getDependencies();
		if (!deps.isEmpty()) {
			thisState = ResolutionState.UNRESOLVED;
			for (PluginComponentId depId : deps) {
				PluginComponent<?> depArc = manager.getResolutionContext().getDependencyTree().get(depId,
						PluginComponent.class);

				boolean optional = depId.isOptional() || (depArc != null && depArc.getComponentId().isOptional());
				try {
					if (depArc == null || !depArc.getState().isResolved()) {
						if (optional && thisState != ResolutionState.PARTIAL)
							thisState = ResolutionState.SATISFACTORY;
						else if (thisState == ResolutionState.PARTIAL) {
							thisState = ResolutionState.PARTIAL;

							/* Wont get any better! */
							break;
						}
					} else if (thisState == ResolutionState.UNRESOLVED)
						thisState = ResolutionState.RESOLVED;
				} catch (IllegalStateException ise) {
					/* Circular dependency? */
					throw ise;
				}

			}
		}
		return thisState;
	}

	protected final PluginManager getManager() {
		return manager;
	}

	protected final void resolveNode(PluginProgressMonitor progress) throws IOException {
		for (PluginComponentId depId : new LinkedHashSet<>(getDependencies())) {
			PluginComponent<?> depArc = manager.getResolutionContext().getDependencyTree().get(depId,
					PluginComponent.class);
			if (depArc != null && !depArc.getState().isResolved()) {
				try {
					depArc.resolve(progress);
				} catch (HaltResolutionException hre) {
					if (depArc.getComponentId().isOptional()) {
						progress.message(MessageType.DEBUG,
								String.format("Ignoring missing optional dependency %s for %s", depArc.getComponentId(),
										getComponentId()));
					} else
						throw hre;
				}
			}
		}
	}
}
