package com.sshtools.forker.plugin.api;

public interface PluginSpec extends PluginNode<PluginArchive> {
	
	boolean isStatic();
	
	void setStatic(boolean staticPlugin);

	boolean isAutostart();

	void setAutostart(boolean autostart);

	Class<?> getResolvedClass();
	
	void setError(Throwable exception);
	
	Throwable getError();

	void reparent(PluginArchive destination);

}