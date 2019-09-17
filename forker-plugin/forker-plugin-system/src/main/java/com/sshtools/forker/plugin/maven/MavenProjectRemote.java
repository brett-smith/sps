package com.sshtools.forker.plugin.maven;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import com.sshtools.forker.plugin.AbstractRemote;
import com.sshtools.forker.plugin.api.PluginArchive;
import com.sshtools.forker.plugin.api.PluginComponentId;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;
import com.sshtools.forker.plugin.api.PluginRemote;

/**
 * Useful in a development environment. Allows a Maven project to be presented
 * as a {@link PluginRemote} that contains a single archive, the project itself.
 * When the archive is retrieved from this remote, where possible the files will
 * be symbolically linked to the original files.
 * <p>
 * The remote supports listing, returning a single {@link PluginArchive}.
 */
public class MavenProjectRemote extends AbstractRemote {

	public MavenProjectRemote(String id, int weight) {
		super(id, weight);
	}

	public MavenProjectRemote(String id) {
		super(id);
	}

	@Override
	public boolean isLocal() {
		return true;
	}

	@Override
	protected URL doRetrieve(PluginProgressMonitor monitor, PluginComponentId archive) throws IOException {
		File dir = new File(getId());
		File pom = new File(dir, "pom.xml");
		File local = manager.getLocal();
		if (!local.exists() && !local.mkdirs())
			throw new IOException(String.format("Could not create local archive directory %s.", local));

		if (pom.exists()) {
			MavenXpp3Reader reader = new MavenXpp3Reader();
			try (Reader in = new FileReader(pom)) {
				try {
					Model model = reader.read(in);
					if (Objects.equals(archive.getGroup(), model.getGroupId())
							&& Objects.equals(archive.getId(), model.getArtifactId())
							&& (!archive.hasVersion() || Objects.equals(archive.getVersion(), model.getVersion()))) {

						File classdir = new File(dir, "target" + File.separator + "classes");
						if (classdir.exists()) {
							archive = archive.withVersion(model.getVersion());

							File destClassdir = new File(local, archive.toFilename());
							FileUtils.deleteDirectory(destClassdir);
							File destPomFile = new File(local, archive.toFilename() + ".pom");

							download(monitor, destClassdir.getName(), classdir.getAbsoluteFile().toURI().toURL(),
									destClassdir);
							download(monitor, destPomFile.getName(), pom.getAbsoluteFile().toURI().toURL(),
									destClassdir);

							return destClassdir.toURI().toURL();
						}
					}
				} catch (XmlPullParserException e) {
					throw new IOException("Could not read POM.", e);
				}
			}
		}
		return null;
	}

	@Override
	protected Set<PluginArchive> doList(PluginProgressMonitor monitor) throws IOException {

		Set<PluginArchive> l = new LinkedHashSet<>();
		File dir = new File(getId());
		File pom = new File(dir, "pom.xml");
		File local = manager.getLocal();
		if (!local.exists() && !local.mkdirs())
			throw new IOException(String.format("Could not create local archive directory %s.", local));

		if (pom.exists()) {
			MavenFolderArchive arc = new MavenFolderArchive(manager, dir, pom,
					new File(dir, "target" + File.separator + "classes").toURI().toURL(), monitor);
			l.add(arc);
		}

		return l;
	}

}