package com.sshtools.forker.plugin.shell;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.Ansi.Attribute;
import org.jline.reader.Candidate;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import com.sshtools.forker.plugin.PluginUtils;
import com.sshtools.forker.plugin.api.PluginArchive;
import com.sshtools.forker.plugin.api.PluginComponentId;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;
import com.sshtools.forker.plugin.api.PluginSpec;

public class Info extends AbstractCommand {

	@Override
	public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) throws IOException {
		if (line.wordIndex() > 0) {
			for (PluginArchive a : manager.getChildren()) {
				String id = a.getComponentId().toFilename();
				if (id.startsWith(line.word()))
					candidates.add(new Candidate(id));
			}
		}
	}

	public void dumpArchiveInfo(PluginComponentId id, PluginProgressMonitor progress) throws IOException {
		PluginArchive arc = manager.getResolutionContext().getDependencyTree().get(id, PluginArchive.class);
		if (arc == null)
			throw new IllegalArgumentException(String.format("No such archive %s", id));
		printInfoLine(0, "Id", arc.getComponentId());
		if (arc.getArchive() != null)
			printInfoLine(0, "Archive", PluginUtils.getPathRelativeToCwd(arc.getArchive()));
		if (arc.getEmbedder() != null)
			printInfoLine(0, "Embedded In", arc.getEmbedder().getComponentId());
		printInfoLine(0, "Class", arc.getClass().getSimpleName());
		printInfoLine(0, "State", arc.getState(), getStateColour(arc.getState()));
		printInfoLine(0, "Optional", arc.getComponentId().isOptional() ? "Yes" : "No");
		printInfoLine(0, "Scope", arc.getScope().name());
		printInfoLine(0, "Hash", arc.getHash());
		printInfoLine(0, "Size", arc.getSize());
		printInfoLine(0, "Type", arc.getComponentId().getType());
		printInfoLine(0, "Pseudo Classpath", String.join(":", arc.getPseudoClasspath()));
		if (!arc.getDependencies().isEmpty()) {
			System.out.println(Ansi.ansi().a("\n").bold().a("Dependencies :-").boldOff().a("\n").toString());
			List<PluginComponentId> allDeps = new ArrayList<>(arc.getAllDependencies());
			Collections.sort(allDeps);
			for (PluginComponentId dep : allDeps) {
				printComponent(dep, progress, 2, true);
			}
		}
		if (!arc.getDependents().isEmpty()) {
			System.out.println(Ansi.ansi().a("\n").bold().a("Dependents :-").boldOff().a("\n").toString());
			List<PluginComponentId> allDeps = new ArrayList<>(arc.getAllDependents());
			Collections.sort(allDeps);
			for (PluginComponentId dep : allDeps) {
				printComponent(dep, progress, 2, true);
			}
		}

		Set<PluginSpec> plugins = arc.getChildren();
		if (!plugins.isEmpty()) {
			System.out.println(Ansi.ansi().a("\n").bold().a("Plugins :-").boldOff().toString());
			for (PluginSpec depSpec : plugins) {
				System.out.println();
				dumpPluginInfo(2, depSpec.getComponentId());
			}
		}
	}

	public void dumpPluginInfo(int indent, PluginComponentId id) throws IOException {
		String ids = spaces.substring(0, indent);
		printInfoLine(indent, "Id", id);
		printInfoLine(indent, "Type", id.getType());
		printInfoLine(indent, "Optional", id.isOptional() ? "Yes" : "No");
		PluginSpec spec = manager.getResolutionContext().getDependencyTree().get(id, PluginSpec.class);
		if (spec != null) {
			printInfoLine(indent, "State", spec.getState(), getStateColour(spec.getState()));
			printInfoLine(indent, "Active", manager.isStarted(spec.getComponentId()) ? "Yes" : "No");
			printInfoLine(indent, "Scope", spec.getScope().name());
			Set<PluginComponentId> deps = spec.getDependencies();
			if (!deps.isEmpty()) {
				System.out.println(String.format("\n%sDependencies :-", ids));
				for (PluginComponentId depSpec : deps) {
					System.out.println();
					dumpPluginInfo(indent + 2, depSpec);
				}
			}
			if (spec.getError() != null) {
				printInfoLine(indent, "Error",
						spec.getError().getMessage() == null ? "No message supplied." : spec.getError().getMessage());
				System.out.println();
				spec.getError().printStackTrace(System.out);
			}
		}
	}

	public void dumpPluginInfo(PluginComponentId id) throws IOException {
		dumpPluginInfo(0, id);
	}

	@Override
	public void exec(PluginProgressMonitor monitor, CommandLine commandLine) throws IOException {
		if (commandLine.getArgList().isEmpty())
			throw new IllegalArgumentException("ERROR: Must supply component ID.");
		else {
			int p = 0;
			for (String sid : expand(commandLine.getArgList(), manager.getChildren())) {
				if (++p > 0) {
					if (isTerminal())
						System.out.print(Ansi.ansi().a(Attribute.UNDERLINE).a(spaces.substring(0, getTerminalWidth()))
								.a(Attribute.UNDERLINE_OFF));
					else {
						System.out.print(dashes.substring(0, getTerminalWidth()));
					}
				}
				PluginComponentId id = new PluginComponentId(sid);
				if (id.hasPlugin()) {
					dumpPluginInfo(id);
				} else {
					dumpArchiveInfo(id, monitor);
				}
			}
		}
	}

	@Override
	public String getDescription() {
		return "Show information about one or more archives or plugins.";
	}

}
