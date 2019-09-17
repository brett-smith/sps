package com.sshtools.forker.plugin.shell;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import com.sshtools.forker.plugin.api.PluginProgressMonitor;
import com.sshtools.forker.plugin.api.PluginSpec;

public class Plugins extends AbstractCommand {

	@Override
	public void exec(PluginProgressMonitor monitor, CommandLine commandLine) throws IOException {
		if (!manager.getState().isResolved())
			manager.resolve(monitor);

		boolean showStarted = commandLine.hasOption('s')
				|| (!commandLine.hasOption('c') && !commandLine.hasOption('r'));
		boolean showClosed = commandLine.hasOption('c') || (!commandLine.hasOption('s') && !commandLine.hasOption('r'));
		boolean showUnresolved = commandLine.hasOption('u');

		Set<PluginSpec> plugins = new HashSet<>(manager.getPlugins());
		if (showUnresolved)
			plugins.addAll(manager.getUnresolvedPlugins());

		for (PluginSpec a : plugins) {
			boolean started = manager.getStartedPlugins().contains(a);
			if ((a.getState().isResolved() || showUnresolved) && ((started && showStarted) || (!started && showClosed)))
				printComponent(a, monitor);
		}
	}

	@Override
	public String getDescription() {
		return "List all currently available plugins.";
	}

	@Override
	protected Options buildOptions() {
		return new Options().addOption("s", "started", false, "Show started plugins.")
				.addOption("c", "closed", false, "Show closed plugins.")
				.addOption("u", "unresolved", false, "Show closed plugins.");
	}
}
