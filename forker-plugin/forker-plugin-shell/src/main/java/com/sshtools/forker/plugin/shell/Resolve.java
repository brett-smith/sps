package com.sshtools.forker.plugin.shell;

import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import com.sshtools.forker.plugin.api.PluginProgressMonitor;

public class Resolve extends AbstractCommand {

	@Override
	public void exec(PluginProgressMonitor monitor, CommandLine commandLine) throws IOException {
		manager.resolve(monitor, commandLine.hasOption('o'), commandLine.hasOption('f'), commandLine.hasOption('i'),
				false, false);
		System.out.println(manager.getState());
	}

	@Override
	public String getDescription() {
		return "Resolve the current dependency tree, optionally installing missing components.";
	}

	@Override
	protected Options buildOptions() {
		return new Options().addOption("o", "optional", false, "Resolve optional archives.")
				.addOption("i", "install", false, "Try to download and install missing archives and plugins.")
				.addOption("f", "fail-on-error", true, "Fail on error and stop.");
	}
}
