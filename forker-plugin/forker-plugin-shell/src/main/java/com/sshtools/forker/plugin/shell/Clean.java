package com.sshtools.forker.plugin.shell;

import java.io.IOException;

import org.apache.commons.cli.CommandLine;

import com.sshtools.forker.plugin.api.PluginProgressMonitor;

public class Clean extends AbstractCommand {

	@Override
	public void exec(PluginProgressMonitor monitor, CommandLine commandLine) throws IOException {
		manager.cleanOrphans(monitor);
	}

	@Override
	public String getDescription() {
		return "Clear orphan archives.";
	}
}
