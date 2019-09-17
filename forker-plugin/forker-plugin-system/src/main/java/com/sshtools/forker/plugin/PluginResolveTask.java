package com.sshtools.forker.plugin;

import java.io.IOException;

import com.sshtools.forker.plugin.api.PluginProgressMonitor;
import com.sshtools.forker.plugin.api.PluginResolveContext;

public interface PluginResolveTask<C> {
	C exec(PluginProgressMonitor monitor, PluginResolveContext resolve) throws IOException;
}
