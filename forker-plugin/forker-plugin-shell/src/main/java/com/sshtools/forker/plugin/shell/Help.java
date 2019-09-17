package com.sshtools.forker.plugin.shell;

import java.io.IOException;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.fusesource.jansi.Ansi;

import com.sshtools.forker.plugin.api.PluginProgressMonitor;

public class Help extends AbstractCommand {

	private PluginShell boot;

	public Help(PluginShell boot) {
		this.boot = boot;
	}

	@Override
	public void exec(PluginProgressMonitor monitor, CommandLine commandLine) throws IOException {
		if (commandLine.getArgList().isEmpty()) {
			int w = terminal.getWidth();
			if (w == 0)
				w = 80;
			w -= 21;
			for (Map.Entry<String, Command> en : boot.getCommands().entrySet()) {
				String description = en.getValue().getDescription();
				if (description.length() > w)
					description = description.substring(0, w);
				System.out.println(String.format("%s%-20s%s %s", Ansi.ansi().bold(), en.getKey(), Ansi.ansi().boldOff(),
						description));
			}
		} else {
			for (String c : commandLine.getArgList()) {
				Command cmd = boot.getCommands().get(c);
				if (cmd != null) {
					commandHelp(c, cmd);
				} else
					throw new IllegalArgumentException(String.format("No such command %s.", c));
			}
		}
	}

	@Override
	public String getDescription() {
		return "Show command help";
	}

}
