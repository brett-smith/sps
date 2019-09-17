package com.sshtools.forker.plugin.shell;

import java.io.IOException;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.jline.reader.Candidate;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import com.sshtools.forker.plugin.api.PluginComponentId;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;
import com.sshtools.forker.plugin.api.PluginSpec;

public class Start extends AbstractCommand {
	@Override
	public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) throws IOException {
		if (line.wordIndex() > 0) {
			for (PluginSpec a : manager.getStoppedPlugins()) {
				String id = a.getComponentId().toFilename();
				if (id.startsWith(line.word()))
					candidates.add(new Candidate(id));
			}
		}
	}

	@Override
	public void exec(PluginProgressMonitor monitor, CommandLine commandLine) throws IOException {
		if (commandLine.getArgList().isEmpty())
			manager.start(monitor);
		else {
			for (String p : commandLine.getArgList())
				manager.start(new PluginComponentId(p), monitor);
		}
	}

	@Override
	public String getDescription() {
		return "Start either all plugins are a list of specified plugins.";
	}

}
