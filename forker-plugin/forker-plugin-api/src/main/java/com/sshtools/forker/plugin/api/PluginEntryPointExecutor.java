package com.sshtools.forker.plugin.api;

public interface PluginEntryPointExecutor {
	void exec(Runnable runnable) throws InterruptedException;
}
