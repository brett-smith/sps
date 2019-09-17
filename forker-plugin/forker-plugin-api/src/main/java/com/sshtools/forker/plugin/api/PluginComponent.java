package com.sshtools.forker.plugin.api;

import java.io.IOException;

public interface PluginComponent<P extends PluginComponent<?>> extends Comparable<PluginComponent<?>> {
	P getParent();

	ClassLoader getClassLoader();
	
	void dirtyState();

	String getName();

	String getDescription();

	PluginComponentId getComponentId();

	void resolve(PluginProgressMonitor progress) throws IOException;

	ResolutionState getState();

	PluginScope getScope();
}
