package com.sshtools.forker.plugin;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import com.sshtools.forker.plugin.api.PluginArchive;

/**
 * Throw when an archive should not be resolved again during this resolution
 * session.
 */
public class HaltResolutionException extends IllegalStateException {

	private static final long serialVersionUID = 1L;

	private Set<PluginArchive> archives = new LinkedHashSet<>();

	public HaltResolutionException(PluginArchive... plugins) {
		super();
		archives.addAll(Arrays.asList(plugins));
	}

	public HaltResolutionException(String s, PluginArchive... plugins) {
		super(s);
		archives.addAll(Arrays.asList(plugins));
	}

	public HaltResolutionException(String message, Throwable cause, PluginArchive... plugins) {
		super(message, cause);
		archives.addAll(Arrays.asList(plugins));
	}

	public HaltResolutionException(Throwable cause, PluginArchive... plugins) {
		super(cause);
		archives.addAll(Arrays.asList(plugins));
	}

	public Set<PluginArchive> getArchives() {
		return archives;
	}

}
