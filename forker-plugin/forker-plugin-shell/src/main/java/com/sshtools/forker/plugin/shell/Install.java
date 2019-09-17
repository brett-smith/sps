package com.sshtools.forker.plugin.shell;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jline.reader.Candidate;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import com.sshtools.forker.plugin.ProgressSink;
import com.sshtools.forker.plugin.api.PluginArchive;
import com.sshtools.forker.plugin.api.PluginComponentId;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;

public class Install extends AbstractCommand {

	@Override
	public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) throws IOException {
		if (line.wordIndex() > 0) {
			for (PluginArchive a : manager.list(new ProgressSink())) {
				String id = a.getComponentId().toFilename();
				if (id.startsWith(line.word())) {
					boolean inst = manager.getInstalled().contains(a);
					boolean avail = manager.getChildren().contains(a);
					if (!inst && !avail)
						candidates.add(new Candidate(id));
				}
			}
		}
	}

	@Override
	public void exec(PluginProgressMonitor monitor, CommandLine commandLine) throws IOException {
		List<PluginComponentId> ids = new ArrayList<>();
		Set<PluginArchive> list = manager.list(new ProgressSink());
		boolean update = commandLine.hasOption('u');
		if (update) {
			if (commandLine.getArgList().isEmpty()) {
				for (PluginArchive arc : list) {
					PluginArchive parc = manager.getResolutionContext().getDependencyTree()
							.get(arc.getComponentId().withoutVersion(), PluginArchive.class);

					if (parc != null) {
						DefaultArtifactVersion availableVersion = new DefaultArtifactVersion(
								arc.getComponentId().getVersion());
						DefaultArtifactVersion installedVersion = new DefaultArtifactVersion(
								parc.getComponentId().getVersion());
						if (availableVersion.compareTo(installedVersion) > 0
								|| (availableVersion.compareTo(installedVersion) == 0
										&& !Objects.equals(arc.getHash(), parc.getHash()))) {
							/* Either new version, or same version with different hash, prevent downgrade */
							ids.add(arc.getComponentId());
						}
					}
				}
			} else
				throw new IllegalArgumentException(
						String.format("Cannot specify any archive ID if you specified updates."));
		} else {
			if (commandLine.getArgList().isEmpty())
				throw new IllegalArgumentException("Must supply at least one component ID.");
			List<String> expand = expand(commandLine.getArgList(), list);
			if (expand.isEmpty())
				throw new IllegalArgumentException("Must supply at least one component ID.");
			for (String p : expand)
				ids.add(new PluginComponentId(p));
		}
		manager.install(monitor, commandLine.hasOption('s'), ids.toArray(new PluginComponentId[0]));
	}

	@Override
	public String getDescription() {
		return "Install an archive or plugin from a remote.";
	}

	@Override
	protected Options buildOptions() {
		return new Options().addOption("s", "start", false, "Start newly installed plugins (overriding autostart).")
				.addOption("u", "update", false, "Install all updates.");
	}

}
