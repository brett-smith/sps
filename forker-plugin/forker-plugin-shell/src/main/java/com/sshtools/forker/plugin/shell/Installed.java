package com.sshtools.forker.plugin.shell;

import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import com.sshtools.forker.plugin.api.PluginArchive;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;
import com.sshtools.forker.plugin.api.PluginSpec;

public class Installed extends AbstractCommand {

	@Override
	public void exec(PluginProgressMonitor monitor, CommandLine commandLine) throws IOException {
		if (!manager.getState().isResolved())
			manager.resolve(monitor);
		boolean verbose = commandLine.hasOption('v');
		for (PluginArchive a : manager.getInstalled()) {
			printComponent(a, monitor, verbose);
			if (commandLine.hasOption('p')) {
				for (PluginSpec s : a.getChildren()) {
					printComponent(s, monitor, 2, verbose);
				}
			}
		}
	}

	@Override
	public String getDescription() {
		return "List all installed archives and optionally plugins.";
	}

	@Override
	protected Options buildOptions() {
		return new Options().addOption("p", "plugins", false, "Show plugins as well.").addOption("v", "verbose", false,
				"Show verbose component details.");
	}

}
