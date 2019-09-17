package com.sshtools.forker.plugin.api;

public interface PluginClasspath {
	default String[] getPath() {
		return new String[0];
	}
}
