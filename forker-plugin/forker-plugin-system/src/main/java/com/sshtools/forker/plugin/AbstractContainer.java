package com.sshtools.forker.plugin;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

import com.sshtools.forker.plugin.api.PluginComponent;
import com.sshtools.forker.plugin.api.PluginComponentId;
import com.sshtools.forker.plugin.api.PluginContainer;
import com.sshtools.forker.plugin.api.PluginManager;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;
import com.sshtools.forker.plugin.api.ResolutionState;

public abstract class AbstractContainer<P extends PluginComponent<?>, C extends PluginComponent<?>>
		extends AbstractNode<P> implements PluginContainer<P, C> {

	public AbstractContainer(PluginManager manager, PluginComponentId component) {
		super(manager, component);
	}

	@Override
	protected ResolutionState calcState() {
		ResolutionState base = calcBaseState();
		if (base.isResolved())
			return calcChildrenState();
		else
			return base;
	}

	@Override
	protected void doResolve(PluginProgressMonitor progress) throws IOException {
		if (!calcBaseState().isResolved() || !calcChildrenState().isComplete())
			throw new UnsupportedOperationException("Cannot resolve this base component.");
	}

	protected final ResolutionState calcChildrenState() {
		ResolutionState thisState = ResolutionState.RESOLVED;
		/*
		 * This component itself is resolved, if any of it's plugins are not resolved
		 * then this component is considered partially resolved
		 */
		Set<C> children = getChildren();
		if (!children.isEmpty()) {
			for (C c : children) {
				if (!c.getState().isResolved()) {
					if (c.getComponentId().isOptional() && thisState != ResolutionState.PARTIAL)
						thisState = ResolutionState.SATISFACTORY;
					else
						thisState = ResolutionState.PARTIAL;
				}
			}
		}
		return thisState;
	}

	protected final void resolveChildren(PluginProgressMonitor progress) throws IOException {
		for (C spec : new LinkedHashSet<>(getChildren())) {
			if (!spec.getState().isResolved()) {
				spec.resolve(progress);
			}
		}
	}

}
