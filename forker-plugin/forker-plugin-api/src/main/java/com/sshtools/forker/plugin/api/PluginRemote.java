package com.sshtools.forker.plugin.api;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.util.Set;

public interface PluginRemote extends Closeable, Comparable<PluginRemote> {

	boolean isEnabled();
	
	void setEnabled(boolean enabled);
	
	String getId();

	int getWeight();

	void init(PluginManager manager) throws IOException;

	boolean isLocal();

	URL retrieve(PluginProgressMonitor monitor, PluginComponentId archive) throws IOException;

	Set<PluginArchive> list(PluginProgressMonitor progress) throws IOException;
}
