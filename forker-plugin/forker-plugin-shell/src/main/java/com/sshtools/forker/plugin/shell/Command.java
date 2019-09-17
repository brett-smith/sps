package com.sshtools.forker.plugin.shell;

import java.io.IOException;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.jline.reader.Candidate;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import org.jline.terminal.Terminal;

import com.sshtools.forker.plugin.api.PluginManager;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;

public interface Command {
	
	void attach(Terminal terminal);

	void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) throws IOException;

	String getDescription();

	Options getOptions();

	void init(PluginManager manager);

	void run(String name, PluginProgressMonitor monitor, CommandLine commandLine) throws IOException;
}
