package com.sshtools.forker.plugin;

import com.sshtools.forker.plugin.api.PluginComponentId;

public class PluginInstance {
	private Object instance;
	private PluginComponentId plugin;
	private Thread thread;

	public PluginInstance(Object instance, PluginComponentId plugin) {
		super();
		this.plugin = plugin;
		this.instance = instance;
	}

	public PluginComponentId getComponentId() {
		return plugin;
	}

	public Object getInstance() {
		return instance;
	}

	public Thread getThread() {
		return thread;
	}

	public void setThread(Thread thread) {
		this.thread = thread;
	}
}
