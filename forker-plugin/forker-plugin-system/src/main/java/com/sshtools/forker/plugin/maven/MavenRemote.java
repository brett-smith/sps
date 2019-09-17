package com.sshtools.forker.plugin.maven;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Set;

import org.apache.commons.lang3.SystemUtils;

import com.sshtools.forker.plugin.AbstractRemote;
import com.sshtools.forker.plugin.api.PluginArchive;
import com.sshtools.forker.plugin.api.PluginComponentId;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;
import com.sshtools.forker.plugin.api.PluginProgressMonitor.MessageType;

public class MavenRemote extends AbstractRemote {

	private URL url;

	static URL url(String url) {
		try {
			return new URL(url);
		} catch (MalformedURLException murle) {
			try {
				return new File(url).toURI().toURL();
			} catch (MalformedURLException murle2) {

				throw new IllegalArgumentException(String.format("Invalid URL %s", url), murle);
			}
		}
	}

	public MavenRemote(String url) {
		this(url, 0);
	}

	public MavenRemote(String url, int weight) {
		super(url, weight);
		this.url = url(url);
	}

	public boolean isLocal() {
		return url.getProtocol().equals("file");
	}

	@Override
	protected URL doRetrieve(PluginProgressMonitor progress, PluginComponentId pa) throws IOException {
		String artPath = pa.getGroup().replaceAll("\\.", "/") + "/" + pa.getId() + "/" + pa.getVersion() + "/"
				+ pa.getId() + "-" + pa.getVersion();
		URL artUrl = new URL(url, artPath + ".jar");
		URL pomUrl = new URL(url, artPath + ".pom");

		File local = manager.getLocal();
		if (!local.exists() && !local.mkdirs())
			throw new IOException(String.format("Could not create local archive directory %s.", local));

		File outFile = new File(local, pa + ".jar");
		File pomFile = new File(local, pa + ".pom");

		if (SystemUtils.IS_OS_UNIX && artUrl.getProtocol().equals("file")) {
			/* We might be able to soft link */
			File f = new File(artUrl.getPath());
			if (f.exists()) {
				Files.createSymbolicLink(outFile.toPath(), f.toPath());
				f = new File(pomUrl.getPath());
				if (f.exists()) {
					if (pomFile.exists())
						pomFile.delete();
					Files.createSymbolicLink(pomFile.toPath(), f.toPath());
				}
				return outFile.toURI().toURL();
			} else
				return null;
		}
		boolean jarOk = false;
		try {
			jarOk = download(progress, pa.getId() + ":" + pa.getVersion(), artUrl, outFile);
		} catch (FileNotFoundException fnfe) {
			if (progress != null)
				progress.message(MessageType.DEBUG, String.format("%s not found at %s.", artUrl, url));
		} catch (IOException fnfe) {
			if (progress != null)
				progress.message(MessageType.WARNING, String.format("Failed to download %s from %s.", artUrl, url));
		}
		try {
			download(progress, pa.getId() + ":" + pa.getVersion(), pomUrl, pomFile);
		} catch (IOException fnfe) {
		}

		return jarOk ? outFile.toURI().toURL() : null;
	}

	@Override
	protected Set<PluginArchive> doList(PluginProgressMonitor progress) {
		return Collections.emptySet();
	}
}
