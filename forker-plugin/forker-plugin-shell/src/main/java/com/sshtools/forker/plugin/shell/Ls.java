package com.sshtools.forker.plugin.shell;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import com.sshtools.forker.plugin.api.PluginArchive;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;
import com.sshtools.forker.plugin.api.PluginScope;
import com.sshtools.forker.plugin.api.PluginSpec;

public class Ls extends AbstractCommand {

	@Override
	public void exec(PluginProgressMonitor monitor, CommandLine commandLine) throws IOException {
		if (!manager.getState().isResolved())
			manager.resolve(monitor);
		boolean showEmbedded = commandLine.hasOption('e');
		boolean showSystem = commandLine.hasOption('s');
		boolean showUnresolved = commandLine.hasOption('u');
		boolean showOptional = commandLine.hasOption('o');
		boolean showResolved = commandLine.hasOption('r');
		boolean showArchives = commandLine.hasOption('a');
		boolean showPlugins = commandLine.hasOption('p');
		boolean verbose = commandLine.hasOption('v');

		if (!showArchives && !showPlugins)
			showArchives = showPlugins = true;

		if (!showEmbedded && !showSystem && !showOptional && !showUnresolved && !showResolved)
			showEmbedded = showSystem = showUnresolved = showOptional = showResolved = true;

		if (showEmbedded && !showUnresolved && !showResolved)
			showResolved = true;

		if (showSystem && !showUnresolved && !showResolved)
			showResolved = true;

		List<PluginArchive> children = new ArrayList<>(manager.getChildren());
		Collections.sort(children);
		for (PluginArchive a : children) {
			if (((a.getEmbedder() == null || showEmbedded) && (a.getScope() != PluginScope.SYSTEM || showSystem)
					&& (a.getState().isResolved() || showUnresolved) && (!a.getState().isResolved() || showResolved)
					&& (!a.getComponentId().isOptional() || showOptional))) {
				if (showArchives)
					printComponent(a, monitor, verbose);
				if (showPlugins) {
					for (PluginSpec p : a.getChildren()) {
						printComponent(p, monitor, showArchives ? 2 : 0, verbose);
					}
				}
			}
		}
	}

	@Override
	public String getDescription() {
		return "List all of the available archives including those on the system path and installed in the local repository. By default, all states are reported. If you supply any filter options, only those states will be reported.";
	}

	@Override
	protected Options buildOptions() {
		return new Options().addOption("e", "embedded", false, "Show embedded archives.")
				.addOption("s", "system", false, "Show system archives.")
				.addOption("a", "archives", false, "Show archives.").addOption("p", "plugins", false, "Show plugins.")
				.addOption("u", "unresolved", false, "Show unresolved archives.")
				.addOption("v", "verbose", false, "Show verbose component details.")
				.addOption("r", "resolved", false, "Show resolved archives.")
				.addOption("o", "optional", false, "Show optional archives.");
	}

}
