package com.sshtools.forker.plugin.shell;

import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.fusesource.jansi.Ansi;

import com.sshtools.forker.plugin.api.PluginProgressMonitor;

public class Cls extends AbstractCommand {

	@Override
	public void exec(PluginProgressMonitor monitor, CommandLine commandLine) throws IOException {
		System.out.print(Ansi.ansi().eraseScreen().cursor(0, 0));
	}

	@Override
	public String getDescription() {
		return "Clear the screen.";
	}
}
