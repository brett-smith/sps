package com.sshtools.forker.plugin.api;

import java.util.Set;

public interface PluginNode<P extends PluginComponent<?>> extends PluginComponent<P> {

	Set<PluginComponentId> getDependents();

	Set<PluginComponentId> getAllDependents();

	Set<PluginComponentId> getAllDependencies();

	void addDependent(PluginComponentId... dep);

	void removeDependent(PluginComponentId... dep);

	Set<PluginComponentId> getDependencies();

	void addDependency(PluginComponentId... dep);

	void removeDependency(PluginComponentId... dep);

	void resetClassLoader();
}
