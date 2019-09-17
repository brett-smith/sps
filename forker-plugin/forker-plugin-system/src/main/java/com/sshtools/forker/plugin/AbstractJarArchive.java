package com.sshtools.forker.plugin;

import java.net.URL;

import com.sshtools.forker.plugin.api.PluginComponentId;
import com.sshtools.forker.plugin.api.PluginManager;

public abstract class AbstractJarArchive extends AbstractArchive {

	public AbstractJarArchive(PluginManager manager, URL archive) {
		this(manager, archive, PluginComponentId.fromURL(archive));
	}

	protected AbstractJarArchive(PluginManager manager, URL archive, PluginComponentId component) {
		super(manager, archive, component);
	}

}
