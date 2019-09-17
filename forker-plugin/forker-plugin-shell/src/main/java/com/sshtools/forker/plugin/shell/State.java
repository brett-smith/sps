package com.sshtools.forker.plugin.shell;

import java.io.IOException;

import org.apache.commons.cli.CommandLine;

import com.sshtools.forker.plugin.api.PluginComponent;
import com.sshtools.forker.plugin.api.PluginComponentId;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;

public class State extends AbstractCommand {

	@Override
	public void exec(PluginProgressMonitor monitor, CommandLine commandLine) throws IOException {
		if (commandLine.getArgList().isEmpty())
			System.out.println(manager.getState());
		else {
			for (String sid : expand(commandLine.getArgList(), manager.getChildren())) {
				PluginComponentId id = new PluginComponentId(sid);
				PluginComponent<?> comp = manager.getResolutionContext().getDependencyTree().get(id,
						PluginComponent.class);
				if (comp != null)
					System.out.println(comp.getState());
			}
		}
	}

	@Override
	public String getDescription() {
		return "Show the current state of dependency tree resolution or of a component.";
	}
}
