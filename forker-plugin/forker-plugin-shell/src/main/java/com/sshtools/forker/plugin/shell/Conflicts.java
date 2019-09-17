package com.sshtools.forker.plugin.shell;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;

import com.sshtools.forker.plugin.api.ConflictStrategy;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;

public class Conflicts extends AbstractCommand {

	@Override
	public void exec(PluginProgressMonitor monitor, CommandLine commandLine) throws IOException {
		List<String> args = commandLine.getArgList();
		if (args.isEmpty())
			System.out.println(manager.getConflictStrategy());
		else if (args.size() == 1) {
			manager.setConflictStrategy(ConflictStrategy.valueOf(args.get(0)));
			PluginShell.USER_PREFS.put(PluginShell.PREF_CONFLICT_STRATEGY, args.get(0));
		} else
			throw new IllegalArgumentException(
					String.format("Expects either no arguments to show conflict strategy, or one of %s.",
							Arrays.asList(ConflictStrategy.values()).stream().map(Object::toString)
									.collect(Collectors.joining(","))));
	}

	@Override
	public String getDescription() {
		return "Show or change the conflict strategy.";
	}

}
