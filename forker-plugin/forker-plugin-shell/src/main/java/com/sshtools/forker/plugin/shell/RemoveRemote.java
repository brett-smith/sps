package com.sshtools.forker.plugin.shell;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.jline.reader.Candidate;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import com.sshtools.forker.plugin.api.PluginProgressMonitor;
import com.sshtools.forker.plugin.api.PluginRemote;

public class RemoveRemote extends AbstractCommand {

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
		if (commandLine.getArgList().size() != 1)
			throw new IllegalArgumentException(
					"A single argument specifying the remote ID should be supplied. This is generally a file path or a URL.");
		String remoteId = commandLine.getArgList().get(0);
		for (PluginRemote r : new ArrayList<>(manager.getRemotes())) {
			if (r.getId().equals(remoteId)) {
				manager.removeRemote(r);
				if (commandLine.hasOption('s'))
					PluginShell.removeRemote(r, true);
				else if (commandLine.hasOption('u'))
					PluginShell.removeRemote(r, false);
				return;
			}
		}
		throw new IllegalArgumentException(String.format("No remote with ID of %s.", remoteId));
	}

	@Override
	public String getDescription() {
		return "Add a new remote";
	}

	protected Options buildOptions() {
		return new Options().addOption("u", "user-persist", false, "Save this remote permanently for the current user.")
				.addOption("s", "system-persist", false, "Save this remote permanently for all users.");
	}
}
