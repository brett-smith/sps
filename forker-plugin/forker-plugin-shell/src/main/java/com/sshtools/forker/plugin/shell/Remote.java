package com.sshtools.forker.plugin.shell;

import java.io.IOException;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.jline.reader.Candidate;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import com.sshtools.forker.plugin.api.PluginProgressMonitor;
import com.sshtools.forker.plugin.api.PluginRemote;

public class Remote extends AbstractCommand {

	@Override
	public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) throws IOException {
		if (line.wordIndex() > 0) {
			for (PluginRemote a : manager.getRemotes()) {
				String id = a.getId();
				if (id.startsWith(line.word()))
					candidates.add(new Candidate(id));
			}
		}
	}

	@Override
	public void exec(PluginProgressMonitor monitor, CommandLine commandLine) throws IOException {
		if (commandLine.getArgList().isEmpty()) {
			for (PluginRemote remote : manager.getRemotes()) {
				printRemote(remote);
			}
		} else if (commandLine.getArgList().size() == 1) {
			for (PluginRemote remote : manager.getRemotes()) {
				if (remote.getId().equals(commandLine.getArgList().get(0))) {
					if (commandLine.hasOption('e')) {
						remote.setEnabled(true);
						if (commandLine.hasOption('u'))
							PluginShell.saveRemote(remote, false);
						else if (commandLine.hasOption('s'))
							PluginShell.saveRemote(remote, true);
					} else if (commandLine.hasOption('d')) {
						remote.setEnabled(false);
						if (commandLine.hasOption('u'))
							PluginShell.saveRemote(remote, false);
						else if (commandLine.hasOption('s'))
							PluginShell.saveRemote(remote, true);
					} else
						printRemote(remote);
					return;
				}
			}
			throw new IllegalArgumentException(String.format("No remote %s.", commandLine.getArgList().get(0)));
		} else
			throw new IllegalArgumentException("Must specify exactly one remote.");

	}

	protected void printRemote(PluginRemote remote) {
		System.out.println(String.format("[%1s] [%-20s] %s", remote.isEnabled() ? "*" : "-",
				remote.getClass().getSimpleName(), remote.getId()));
	}

	@Override
	public String getDescription() {
		return "List all of the remotes, locations where archives and plugins may be installed from, add them, remove them or change their enabled state.";
	}

	@Override
	protected Options buildOptions() {
		return new Options().addOption("e", "enable", false, "Enable a remote.")
				.addOption("u", "user-persist", false, "Save this remote permanently for the current user.")
				.addOption("s", "system-persist", false, "Save this remote permanently for all users.")
				.addOption("d", "disable", false, "Disable a remote.");
	}
}
