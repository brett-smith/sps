package com.sshtools.forker.plugin.maven;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;

import com.sshtools.forker.plugin.AbstractArchiveWithDependencies;
import com.sshtools.forker.plugin.api.PluginManager;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;
import com.sshtools.forker.plugin.api.ResolutionState;

public class MavenFolderArchive extends AbstractArchiveWithDependencies {

	private File pom;
	private boolean dependenciesResolved;

	public MavenFolderArchive(PluginManager manager, File projDir, File pom, URL classpath,
			PluginProgressMonitor monitor) throws IOException {
		super(manager, projDir.toURI().toURL());
		setClasspath(Arrays.asList(classpath));
		this.pom = pom;
		POMReader r = new POMReader().resolveDependencies(false);
		try (InputStream in = new FileInputStream(pom)) {
			r.readPOM(this, in, monitor);
		}
	}

	@Override
	protected ResolutionState calcState() {
		ResolutionState base = calcBaseState();
		if (base.isResolved()) {
			base = calcArchiveState();
			if (base.isResolved()) {
				base = calcPOMState();
				if (base.isResolved())
					base = calcPluginsState();
				if (base.isResolved())
					base = calcChildrenState();
			}
		}
		return base;
	}

	@Override
	protected void doResolve(PluginProgressMonitor monitor) throws IOException {
		if (!calcArchiveState().isResolved())
			resolveArchive(monitor);

		if (!calcPOMState().isResolved()) {
			resolvePOM(monitor);
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
		POMReader r = new POMReader().resolveDependencies(true);
		try (InputStream in = new FileInputStream(pom)) {
			r.readPOM(this, in, monitor);
		}
		dependenciesResolved = true;
		getManager().getResolutionContext().getDependencyTree().add(this, monitor);

	}
}
