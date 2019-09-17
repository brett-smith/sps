package com.sshtools.forker.plugin.shell;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.jline.reader.Candidate;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import com.sshtools.forker.plugin.api.PluginArchive;
import com.sshtools.forker.plugin.api.PluginComponentId;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;
import com.sshtools.forker.plugin.api.PluginScope;

public class Uninstall extends AbstractCommand {

	@Override
	public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) throws IOException {
		if (line.wordIndex() > 0) {
			for (PluginArchive a : manager.getChildren()) {
				if (a.getScope() == PluginScope.INSTALLED && a.getState().isResolved()) {
					String id = a.getComponentId().toFilename();
					if (id.startsWith(line.word()))
						candidates.add(new Candidate(id));
				}
			}
		}
	}

	@Override
	public void exec(PluginProgressMonitor monitor, CommandLine commandLine) throws IOException {
		if (commandLine.getArgList().isEmpty())
			throw new IllegalArgumentException("Must supply at least one component ID.");
		else {
			List<PluginComponentId> ids = new ArrayList<>();
			for (String p : expand(commandLine.getArgList(), manager.getInstalled()))
				ids.add(new PluginComponentId(p));
			manager.uninstall(monitor, ids.toArray(new PluginComponentId[0]));
		}
	}

	@Override
	public String getDescription() {
		return "Uninstall one or more archives or plugins.";
	}

}
