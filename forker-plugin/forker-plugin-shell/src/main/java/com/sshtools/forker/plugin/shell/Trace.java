package com.sshtools.forker.plugin.shell;

import java.io.IOException;
import java.util.List;

import org.apache.commons.cli.CommandLine;

import com.sshtools.forker.plugin.api.PluginProgressMonitor;

public class Trace extends AbstractCommand {
	
	private PluginShell shell;

	public Trace(PluginShell shell) {
		this.shell = shell;
	}

	@Override
	public void exec(PluginProgressMonitor monitor, CommandLine commandLine) throws IOException {
		List<String> args = commandLine.getArgList();
		if (args.isEmpty()) 
			System.out.println(manager.isTraces() ? "on" : "off");
		else if (args.size() == 1 && (args.get(0).equals("on") || args.get(0).equals("off"))) {
			shell.setTraces(args.get(0).equals("on"));
		} else
			throw new IllegalArgumentException(
					"Expects either no arguments to show trace state, or a value of 'on' or 'off'.");
	}

	@Override
	public String getDescription() {
		return "Turn stack traces on or off for some error conditions.";
	}

}
