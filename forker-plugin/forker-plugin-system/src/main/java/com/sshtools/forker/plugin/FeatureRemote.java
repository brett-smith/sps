package com.sshtools.forker.plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import com.sshtools.forker.plugin.api.PluginArchive;
import com.sshtools.forker.plugin.api.PluginComponentId;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;

public class FeatureRemote extends AbstractRemote {

	public FeatureRemote(String id) {
		super(id);
	}

	public FeatureRemote(String id, int weight) {
		super(id, weight);
	}

	public URL getURL() {
		try {
			return new URL(getId());
		} catch (MalformedURLException e) {
			try {
				return new File(getId()).toURI().toURL();
			} catch (MalformedURLException e2) {
				throw new IllegalArgumentException("Invalid location.", e2);
			}
		}
	}

	@Override
	public boolean isLocal() {
		return getURL().getProtocol().equals("file");
	}

	@Override
	protected Set<PluginArchive> doList(PluginProgressMonitor progress) throws IOException {
		Set<PluginArchive> l = new HashSet<>();
		URL url = new URL(getURL(), "features.txt");
		URLConnection conx = url.openConnection();
		InputStream inputStream = conx.getInputStream();
		if (inputStream != null) {
			String line;
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
				while ((line = reader.readLine()) != null) {
					line = line.trim();
					if (!line.startsWith("#") && line.length() > 0) {
						PluginArchive pa = new FeatureArchive(manager, new URL(getURL(), line.trim()), progress, true);
						l.add(pa);
						for (PluginComponentId dep : pa.getDependencies()) {
							PluginArchive depArc = manager.getResolutionContext().getDependencyTree().get(dep,
									PluginArchive.class);
							l.add(depArc);
						}
					}
				}
			}
		}
		return l;
	}

	@Override
	protected URL doRetrieve(PluginProgressMonitor monitor, PluginComponentId archive) throws IOException {

		File local = manager.getLocal();
		if (!local.exists() && !local.mkdirs())
			throw new IOException(String.format("Could not create local archive directory %s.", local));

		if (!archive.hasVersion()) {
			/*
			 * If there is no version provided, we list the features in this feature and
			 * find the highest version we have
			 */
			ArtifactVersion latest = null;
			for (PluginArchive a : list(monitor)) {
				if (a.getComponentId().idAndGroup().equals(archive.idAndGroup())) {
					ArtifactVersion v1 = new DefaultArtifactVersion(a.getComponentId().getVersion());
					if (latest == null || v1.compareTo(latest) > 0)
						latest = v1;
				}
			}
			if (latest == null)
				return null;
			else
				archive = archive.withVersion(latest.toString());
		}

		URL url = new URL(getURL(), archive.toFilename() + ".xml");
		try {
			File localFile = new File(manager.getLocal(), archive.toFilename() + ".xml");
			if (download(monitor, archive.toFilename(), url, localFile))
				return localFile.toURI().toURL();
		} catch (FileNotFoundException fnfe) {
			//
		}

		url = new URL(getURL(), archive.toFilename() + ".pom");
		try {
			File localFile = new File(manager.getLocal(), archive.toFilename() + ".pom");
			download(monitor, archive.toFilename(), url, localFile);
		} catch (FileNotFoundException fnfe) {
			//
		}

		url = new URL(getURL(), archive.toFilename() + ".jar");
		try {
			File localFile = new File(manager.getLocal(), archive.toFilename() + ".jar");
			if (download(monitor, archive.toFilename(), url, localFile))
				return localFile.toURI().toURL();
		} catch (FileNotFoundException fnfe) {
			//
		}

		return null;
	}

}
