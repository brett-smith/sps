package com.sshtools.forker.plugin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;

import com.sshtools.forker.plugin.api.InstallMode;
import com.sshtools.forker.plugin.api.PluginArchive;
import com.sshtools.forker.plugin.api.PluginComponentId;
import com.sshtools.forker.plugin.api.PluginManager;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;
import com.sshtools.forker.plugin.api.PluginRemote;

public abstract class AbstractRemote implements PluginRemote {

	protected PluginManager manager;

	private String id;
	private int weight;
	private boolean enabled = true;

	protected AbstractRemote(String id) {
		this(id, 0);
	}

	protected AbstractRemote(String id, int weight) {
		this.id = id;
		this.weight = weight;
	}

	@Override
	public void close() throws IOException {
	}

	@Override
	public int compareTo(PluginRemote o) {
		Boolean b1 = isLocal();
		Boolean b2 = o.isLocal();
		int v = b1.compareTo(b2) * -1;
		if (v == 0) {
			v = Integer.valueOf(weight).compareTo(o.getWeight());
		}
		return v;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AbstractRemote other = (AbstractRemote) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		return true;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public int getWeight() {
		return weight;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		return result;
	}

	@Override
	public void init(PluginManager manager) throws IOException {
		this.manager = manager;
	}

	@Override
	public final URL retrieve(PluginProgressMonitor monitor, PluginComponentId archive) throws IOException {
		if (!isEnabled())
			throw new IllegalStateException(String.format("Repository %s is not enabled.", getId()));
		return doRetrieve(monitor, archive);
	}

	protected abstract URL doRetrieve(PluginProgressMonitor monitor, PluginComponentId archive) throws IOException;

	@Override
	public Set<PluginArchive> list(PluginProgressMonitor progress) throws IOException {
		if (!isEnabled())
			throw new IllegalStateException(String.format("Repository %s is not enabled.", getId()));
		return doList(progress);
	}

	protected abstract Set<PluginArchive> doList(PluginProgressMonitor progress) throws IOException;

	protected boolean download(PluginProgressMonitor progress, String message, URL url, File outFile)
			throws IOException, FileNotFoundException {
		InstallMode mode = manager.getInstallMode();
		if (((SystemUtils.IS_OS_UNIX && mode == InstallMode.AUTO) || mode == InstallMode.LINK)
				&& url.getProtocol().equals("file")) {
			/* We might be able to soft link */
			File f = new File(url.getPath()).getAbsoluteFile();
			if (f.exists()) {
				if (outFile.exists() && !outFile.delete())
					throw new IOException(
							String.format("Could not delete %s to replace it with symbolic link to %s.", outFile, f));
				Files.createSymbolicLink(outFile.toPath(), f.toPath());
				return true;
			} else
				return false;
		}

		if (mode == InstallMode.COPY || mode == InstallMode.AUTO || (!url.getProtocol().equals("file"))) {

			if (url.getProtocol().equals("file")) {
				File f = new File(url.getPath()).getAbsoluteFile();
				if (f.isDirectory()) {
					// TODO progress
					FileUtils.copyDirectory(f, outFile);
					return true;
				}
			}

			URLConnection conx = url.openConnection();
			long len = conx.getContentLengthLong();
			if (len == -1) {
				throw new IOException(String.format("No content length for %s", url));
			}
			if (progress != null)
				progress.start(len);
			try (InputStream in = conx.getInputStream()) {
				byte[] buf = new byte[1024 * 1024];
				long t = 0;
				try (OutputStream out = new FileOutputStream(outFile)) {
					int r;
					while ((r = in.read(buf)) != -1) {
						t += r;
						out.write(buf, 0, r);
						if (progress != null)
							progress.progress(t, message);
					}
					out.flush();
				}
				return true;
			} finally {
				progress.end();
			}
		}

		throw new IllegalStateException(String.format("Install mode %s is not supported on this platform for this URL. Cannot install %s", mode, url));
	}

}
