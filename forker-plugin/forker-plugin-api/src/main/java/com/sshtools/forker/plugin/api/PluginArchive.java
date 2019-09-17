package com.sshtools.forker.plugin.api;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Set;

/**
 * A single plugin archive can contain multiple {@link PluginSpec}. An archive
 * is the internal representation of a group of classes, such as a Maven project
 * artifact, a class folder, or some other container that plugins located in.
 */
public interface PluginArchive extends PluginContainer<PluginManager, PluginSpec>, PluginNode<PluginManager> {

	URL getArchive();

	Collection<URL> getClasspath();

	/**
	 * If this archive is actually contained inside another (for example if it is a
	 * 'shaded' jar, then this will return a non-null value. If this is an actual
	 * actual such as a standard Maven artifact then null will be returned.
	 * 
	 * @return embedded
	 */
	PluginArchive getEmbedder();

	Set<String> getPseudoClasspath();

	String getHash();

	long getSize();

	void setName(String name);

	void setDescription(String description);

	void setComponent(PluginComponentId component);

	void delete() throws IOException;

	void setSize(long size);

	void setHash(String hash);

	void setArchive(URL archive);

	void setClasspath(Collection<URL> classpath);
}
