package com.sshtools.forker.plugin;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import com.sshtools.forker.plugin.api.PluginComponent;
import com.sshtools.forker.plugin.api.PluginComponentId;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;
import com.sshtools.forker.plugin.api.PluginScope;
import com.sshtools.forker.plugin.api.ResolutionState;

public abstract class AbstractComponent<P extends PluginComponent<?>> implements PluginComponent<P> {

	private static ThreadLocal<Set<PluginComponentId>> cleaned = new ThreadLocal<>();
	private PluginComponentId component;
	private String description;

	private String name;
	private ResolutionState cachedState;
	private ThreadLocal<Boolean> calculatingState = new ThreadLocal<>();

	public AbstractComponent(PluginComponentId component) {
		this.component = component;
	}

	@Override
	public PluginScope getScope() {
		return PluginScope.SYSTEM;
	}

	@Override
	public final boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		AbstractComponent<?> other = (AbstractComponent<?>) obj;
		if (component == null) {
			if (other.component != null)
				return false;
		} else if (!component.equals(other.component))
			return false;
		return true;
	}

	@Override
	public PluginComponentId getComponentId() {
		return component;
	}

	public String getDescription() {
		return description;
	}

	public String getName() {
		return name;
	}

	@Override
	public final ResolutionState getState() {
		if (cachedState == null) {
			if (calculatingState.get() == null) {
				try {
					calculatingState.set(Boolean.TRUE);
					cachedState = calcState();
				} finally {
					calculatingState.remove();
				}
			} else {
				// TODO really? what causes this recursive call anyway?
//				throw new IllegalStateException(String.format("Recursively calculating state for %s.", getComponentId()));
				return ResolutionState.UNRESOLVED;
			}
		}
		return cachedState;
	}
	
	@Override
	public int compareTo(PluginComponent<?> o) {
		return getComponentId().compareTo(o.getComponentId());
	}

	public boolean isCalculatingState(AbstractComponent<?> component) {
		return calculatingState.get() != null;
	}

	@Override
	public final void dirtyState() {
		Set<PluginComponentId> done = cleaned.get();
		boolean rootCall = false;
		if (done == null) {
			rootCall = true;
			done = new HashSet<>();
			cleaned.set(done);
		}
		try {
			PluginComponentId cid = getComponentId();
			if (!done.contains(cid)) {
				done.add(cid);
				cachedState = null;
				if (getParent() != null)
					getParent().dirtyState();
				onDirtyState();
			}
		} finally {
			if (rootCall) {
				cleaned.remove();
			}
		}
	}

	protected void onDirtyState() {

	}

	protected ResolutionState calcState() {
		return calcBaseState();

	}

	@Override
	public final int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((component == null) ? 0 : component.hashCode());
		return result;
	}

	@Override
	public final void resolve(PluginProgressMonitor progress) throws IOException {
		doResolve(progress);
		dirtyState();
	}

	protected void doResolve(PluginProgressMonitor progress) throws IOException {
		if (!calcBaseState().isResolved())
			throw new UnsupportedOperationException("Cannot resolve this base component.");
	}

	public final void setComponent(PluginComponentId component) {
		this.component = component;
		dirtyState();
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public void setName(String name) {
		this.name = name;
	}

	protected final ResolutionState calcBaseState() {
		if (getComponentId() != null && getComponentId().isValid()) {
			return ResolutionState.RESOLVED;
		} else
			return ResolutionState.UNRESOLVED;
	}
}
