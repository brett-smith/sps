package com.sshtools.forker.plugin;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import com.sshtools.forker.plugin.api.PluginArchive;

/**
 * Throw to interrupt a resolution session, but try this archive again on the
 * next cycle.
 */
public class ResolutionRetryException extends IllegalStateException {

	private static final long serialVersionUID = 1L;

	private Set<PluginArchive> archives = new LinkedHashSet<>();

	public ResolutionRetryException(PluginArchive... plugins) {
		super();
		archives.addAll(Arrays.asList(plugins));
	}

	public ResolutionRetryException(String s, PluginArchive... plugins) {
		super(s);
		archives.addAll(Arrays.asList(plugins));
	}

	public ResolutionRetryException(String message, Throwable cause, PluginArchive... plugins) {
		super(message, cause);
		archives.addAll(Arrays.asList(plugins));
	}

	public ResolutionRetryException(Throwable cause, PluginArchive... plugins) {
		super(cause);
		archives.addAll(Arrays.asList(plugins));
	}

	public Set<PluginArchive> getArchives() {
		return archives;
	}

}
