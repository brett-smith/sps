package com.sshtools.forker.plugin.api;

import java.util.Set;

public interface PluginContainer<P extends PluginComponent<?>, C extends PluginComponent<?>> extends PluginComponent<P> {

	Set<C> getChildren();
	
	@SuppressWarnings("unchecked")
	boolean addChild(C... child);
	
	@SuppressWarnings("unchecked")
	boolean removeChild(C... child);
}
