package com.sshtools.forker.plugin.shell;

import java.io.IOException;
import java.util.Objects;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import com.sshtools.forker.plugin.api.ArchiveType;
import com.sshtools.forker.plugin.api.PluginArchive;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;
import com.sshtools.forker.plugin.api.PluginScope;
import com.sshtools.forker.plugin.api.PluginSpec;

public class Available extends AbstractCommand {

	@Override
	public void exec(PluginProgressMonitor monitor, CommandLine commandLine) throws IOException {
		if (!manager.getState().isResolved())
			manager.resolve(monitor);
		boolean showArchives = commandLine.hasOption('a');
		boolean showPlugins = commandLine.hasOption('p');
		boolean showFeatures = commandLine.hasOption('f');
		if (!showArchives && !showPlugins && !showFeatures)
			showArchives = showPlugins = showFeatures = true;
		boolean verbose = commandLine.hasOption('v');

		boolean showInstalled = commandLine.hasOption('i');
		boolean showUpdates = commandLine.hasOption('u');
		boolean showInstallable = commandLine.hasOption('d');

		if (!showInstalled && !showUpdates && !showInstallable)
			showUpdates = showInstallable = true;

		for (PluginArchive a : manager.list(monitor)) {
			boolean inst = manager.getInstalled().contains(a);
			boolean avail = !manager.getChildren().contains(a);
			String updateable = null;
			if (showUpdates) {
				PluginArchive existing = manager.getResolutionContext().getDependencyTree()
						.get(a.getComponentId().withoutVersion(), PluginArchive.class);
				if (existing != null && existing.getScope() != PluginScope.SYSTEM) {
					if (!existing.getComponentId().equals(a.getComponentId())) {
						/* Version differs */
						DefaultArtifactVersion availableVersion = new DefaultArtifactVersion(
								a.getComponentId().getVersion());
						DefaultArtifactVersion installedVersion = existing.getScope() == PluginScope.UNREAL ? null
								: new DefaultArtifactVersion(existing.getComponentId().getVersion());
						if (installedVersion != null && availableVersion.compareTo(installedVersion) > 0) {
							updateable = availableVersion.toString();
							a = existing;
						}
					} else if (!Objects.equals(existing.getHash(), a.getHash())) {
						/* Hash differs */
						updateable = a.getComponentId().getVersion().toString();
						a = existing;
					}
				}
			}

			if ((inst && showInstalled) || (updateable != null && showUpdates) || (!inst && avail && showInstallable)) {

				if ((a.getComponentId().getType() == ArchiveType.FEATURE && showFeatures)
						|| (a.getComponentId().getType() != ArchiveType.FEATURE && (showArchives || showPlugins))) {
					if (showArchives || showFeatures) {
						if (updateable == null)
							printComponent(a, monitor, verbose);
						else
							printComponent(a, monitor, verbose, "->", updateable);
					}
					if (showPlugins) {
						for (PluginSpec s : a.getChildren()) {
							printComponent(s, monitor, showArchives || showFeatures ? 2 : 0, verbose);
						}
					}
				}
			}
		}
	}

	@Override
	public String getDescription() {
		return "List all of the archives available in the configured remote repositories, i.e. those that you may install.";
	}

	@Override
	protected Options buildOptions() {
		return new Options().addOption("p", "plugins", false, "Show plugins.")
				.addOption("f", "features", false, "Show only features.")
				.addOption("v", "verbose", false, "Show verbose component details.")
				.addOption("", "verbose", false, "Show verbose component details.")
				.addOption("u", "update", false, "Show updates.")
				.addOption("i", "installed", false, "Show installed archives that do not need updating.")
				.addOption("d", "installable", false, "Show archve that can be installed.")
				.addOption("a", "archives", false, "Show archives.");
	}
}
