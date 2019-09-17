package com.sshtools.forker.plugin.api;

import java.io.File;
import java.io.IOException;
import java.util.Set;

public interface PluginDependencyTree {

	void add(PluginArchive archive, PluginProgressMonitor monitor);

	void remove(PluginArchive archive);

	PluginArchive addFile(PluginProgressMonitor monitor, File file) throws IOException;

	Set<PluginArchive> getResolved();

	<T extends PluginComponent<?>> T get(PluginComponentId key, Class<T> clazz);

	<T extends PluginComponent<?>> Set<T> list(PluginComponentId key, Class<T> clazz);

	Set<PluginArchive> resolve(PluginProgressMonitor monitor, boolean reset) throws IOException;

	PluginClasspath getClassPath();

	Set<PluginSpec> getUnresolvedPlugins();

	<T extends PluginComponent<?>> T newest(PluginComponentId key, Class<T> clazz);

}