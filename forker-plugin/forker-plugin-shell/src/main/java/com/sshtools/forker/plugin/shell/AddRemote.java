package com.sshtools.forker.plugin.shell;

import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import com.sshtools.forker.plugin.api.PluginProgressMonitor;
import com.sshtools.forker.plugin.api.PluginRemote;

public class AddRemote extends AbstractCommand {

	@Override
	public void exec(PluginProgressMonitor monitor, CommandLine commandLine) throws IOException {
		String type = commandLine.getOptionValue('t');
		if (commandLine.getArgList().size() != 1)
			throw new IllegalArgumentException(
					"A single argument specifying the remote ID should be supplied. This is generally a file path or a URL.");
		int weight = 0;
		if (commandLine.hasOption('w')) {
			weight = Integer.parseInt(commandLine.getOptionValue('w'));
		}
		PluginRemote remote = PluginShell.createRemote(type, commandLine.getArgList().get(0), weight, true);
		manager.addRemote(remote);
		if (commandLine.hasOption('s'))
			PluginShell.saveRemote(remote, true);
		else if (commandLine.hasOption('u'))
			PluginShell.saveRemote(remote, false);
	}

	@Override
	public String getDescription() {
		return "Add a new remote";
	}

	@Override
	protected Options buildOptions() {
		return new Options().addOption("t", "type", true, "The remote type, one of maven, maven-project or feature.")
				.addOption("u", "user-persist", false, "Save this remote permanently for the current user.")
				.addOption("w", "weight", true, "The weight to assign to this remote, used for sorting.")
				.addOption("s", "system-persist", false, "Save this remote permanently for all users.");
	}
}
