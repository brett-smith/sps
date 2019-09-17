package com.sshtools.forker.plugin.shell;

import java.io.IOException;
import java.util.List;

import org.apache.commons.cli.CommandLine;

import com.sshtools.forker.plugin.api.PluginProgressMonitor;

public class Autostart extends AbstractCommand {

	public Autostart() {
	}

	@Override
	public void exec(PluginProgressMonitor monitor, CommandLine commandLine) throws IOException {
		List<String> args = commandLine.getArgList();
		if (args.isEmpty())
			System.out.println(manager.isAutostart() ? "on" : "off");
		else {
			boolean as = args.get(0).equals("on");
			if (args.size() == 1 && (as || args.get(0).equals("off"))) {
				manager.setAutostart(as);
				PluginShell.USER_PREFS.putBoolean(PluginShell.PREF_AUTOSTART, as);
			} else
				throw new IllegalArgumentException(
						"Expects either no arguments to show autostart start, or a value of 'on' or 'off'.");
		}
	}

	@Override
	public String getDescription() {
		return "Show the state of, or turn on or off autostarting of plugins.";
	}

}
