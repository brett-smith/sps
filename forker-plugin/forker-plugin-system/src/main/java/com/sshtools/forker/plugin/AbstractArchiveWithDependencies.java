package com.sshtools.forker.plugin;

import java.io.IOException;
import java.net.URL;

import com.sshtools.forker.plugin.api.PluginArchive;
import com.sshtools.forker.plugin.api.PluginComponentId;
import com.sshtools.forker.plugin.api.PluginManager;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;
import com.sshtools.forker.plugin.api.ResolutionState;

public abstract class AbstractArchiveWithDependencies extends AbstractArchive implements PluginArchive {

	public AbstractArchiveWithDependencies(PluginManager manager, URL archive) {
		super(manager, archive);
	}

	public AbstractArchiveWithDependencies(PluginManager manager, URL archive, PluginComponentId component) {
		super(manager, archive, component);
	}

	@Override
	protected ResolutionState calcState() {
		ResolutionState base = calcBaseState();
		if (base.isResolved()) {
			base = calcArchiveState();
			if (base.isResolved()) {
				base = calcNodeState();
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
	protected void doResolve(PluginProgressMonitor progress) throws IOException {
		if (!calcArchiveState().isResolved())
			resolveArchive(progress);

		if (!calcNodeState().isResolved())
			resolveNode(progress);

		resolvePluginsState(progress);
		resolveChildren(progress);

	}

}
