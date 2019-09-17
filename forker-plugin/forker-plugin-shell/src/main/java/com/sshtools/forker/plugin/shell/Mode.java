package com.sshtools.forker.plugin.shell;

import java.io.IOException;
import java.util.List;

import org.apache.commons.cli.CommandLine;

import com.sshtools.forker.plugin.api.InstallMode;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;

public class Mode extends AbstractCommand {

	@Override
	public void exec(PluginProgressMonitor monitor, CommandLine commandLine) throws IOException {
		List<String> args = commandLine.getArgList();
		if (args.isEmpty())
			System.out.println(manager.getInstallMode());
		else if (args.size() == 1) {
			manager.setInstallMode(InstallMode.valueOf(args.get(0)));
			PluginShell.USER_PREFS.put(PluginShell.PREF_MODE, args.get(0));
		} else
			throw new IllegalArgumentException(
					"Expects either no arguments to show trace state, or a value of 'on' or 'off'.");
	}

	@Override
	public String getDescription() {
		return "Show or change install mode.";
	}

}
