package com.sshtools.forker.plugin.shell;

import java.io.IOException;

import org.apache.commons.cli.CommandLine;

import com.sshtools.forker.plugin.api.PluginProgressMonitor;

public class Classpath extends AbstractCommand {

	@Override
	public void exec(PluginProgressMonitor monitor, CommandLine commandLine) throws IOException {
		System.out
				.println(String.join(":", manager.getResolutionContext().getDependencyTree().getClassPath().getPath()));
	}

	@Override
	public String getDescription() {
		return "List the system classpath.";
	}
}
