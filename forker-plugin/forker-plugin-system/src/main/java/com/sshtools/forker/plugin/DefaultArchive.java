package com.sshtools.forker.plugin;

import java.io.IOException;
import java.net.URL;

import com.sshtools.forker.plugin.api.PluginComponentId;
import com.sshtools.forker.plugin.api.PluginDependencyTree;
import com.sshtools.forker.plugin.api.PluginManager;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;
import com.sshtools.forker.plugin.api.ResolutionState;

public class DefaultArchive extends AbstractArchive {

	private boolean transformed;

	public DefaultArchive(PluginManager manager, URL archive, PluginComponentId component) {
		this(manager, archive, component, false);
	}

	public DefaultArchive(PluginManager manager, URL archive, PluginComponentId component, boolean transformed) {
		super(manager, archive, component);
		this.transformed = transformed;
	}

	@Override
	public void delete() throws IOException {
		throw new UnsupportedOperationException(String.format("%s is not deleteable.", getComponentId()));
	}

	@Override
	protected ResolutionState calcState() {
		ResolutionState base = calcBaseState();
		if (base.isResolved()) {
			base = calcArchiveState();
			if (base.isResolved()) {
				base = calcTransformedState();
			}
		}

		return base;
	}

	protected ResolutionState calcTransformedState() {
		return !transformed && getArchive().getProtocol().equals("file") ? ResolutionState.UNRESOLVED
				: ResolutionState.RESOLVED;
	}

	@Override
	protected void doResolve(PluginProgressMonitor progress) throws IOException {

		if (!calcArchiveState().isResolved())
			resolveArchive(progress);

		/*
		 * We now have the archive, we can try to rediscover what sort of artifact it is
		 * if it is a local file. This goes back into the dependency tree and replaces
		 * this instance
		 */
		if (!transformed) {
			PluginDependencyTree depTree = getManager().getResolutionContext().getDependencyTree();
			try {
				depTree.addFile(progress, PluginUtils.urlToFile(getArchive()));
			} finally {
				transformed = true;
			}
		}

	}
}
