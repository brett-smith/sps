package com.sshtools.forker.plugin.api;

public abstract class PluginResolveContext {

	private boolean failOnError = true;
	private boolean resolveOptional = false;
	private boolean download = false;
	private boolean startAutoStartPlugins = false;
	private boolean forcecStartPlugins = false;
	private boolean resolvePlugins = true;
	private String[] arguments = new String[0];
	private ConflictStrategy conflictStrategy;

	public PluginResolveContext() {
	}

	public boolean resolvePlugins() {
		return resolvePlugins;
	}

	public PluginResolveContext resolvePlugins(boolean resolvePlugins) {
		this.resolvePlugins = resolvePlugins;
		return this;
	}

	public PluginResolveContext failOnError(boolean failOnError) {
		this.failOnError = failOnError;
		return this;
	}

	public PluginResolveContext resolveOptional(boolean resolveOptional) {
		this.resolveOptional = resolveOptional;
		return this;
	}

	public boolean download() {
		return download;
	}

	public PluginResolveContext download(boolean download) {
		this.download = download;
		return this;
	}

	public ConflictStrategy conflictStrategy() {
		return conflictStrategy;
	}

	public PluginResolveContext conflictStrategy(ConflictStrategy conflictStrategy) {
		this.conflictStrategy = conflictStrategy;
		return this;
	}

	public boolean resolveOptional() {
		return resolveOptional;
	}

	public boolean failOnError() {
		return failOnError;
	}

	public boolean forceStartPlugins() {
		return forcecStartPlugins;
	}

	public PluginResolveContext forceStartPlugins(boolean forcecStartPlugins) {
		this.forcecStartPlugins = forcecStartPlugins;
		return this;
	}

	public boolean startAutostartPlugins() {
		return startAutoStartPlugins;
	}

	public PluginResolveContext startAutostartlugins(boolean startPlugins) {
		this.startAutoStartPlugins = startPlugins;
		return this;
	}

	public abstract PluginDependencyTree getDependencyTree();

	public String[] arguments() {
		return arguments;
	}

	public PluginResolveContext arguments(String[] arguments) {
		this.arguments = arguments;
		return this;
	}
}
