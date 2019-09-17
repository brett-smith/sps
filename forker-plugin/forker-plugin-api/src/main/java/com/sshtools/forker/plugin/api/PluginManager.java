package com.sshtools.forker.plugin.api;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Set;

public interface PluginManager extends PluginContainer<PluginComponent<?>, PluginArchive>, Closeable {

	void setEntryPointExecutor(PluginEntryPointExecutor entryPointExecutor);

	PluginEntryPointExecutor getEntryPointExecutor();

	void addRemote(PluginRemote remote) throws IOException;

	void uninstall(PluginProgressMonitor progress, PluginComponentId... ids) throws IOException;

	void closePlugin(PluginSpec spec, PluginProgressMonitor progress);

	void closePlugin(String componentId, PluginProgressMonitor monitor);

	URL downloadArchive(PluginProgressMonitor progress, PluginComponentId pa) throws IOException;

	Set<PluginArchive> getInstalled() throws IOException;

	ConflictStrategy getConflictStrategy();

	PluginResolveContext getResolutionContext();

	File getLocal();

	List<PluginRemote> getRemotes();

	Set<PluginArchive> getUnresolvedArchives() throws IOException;

	Set<PluginSpec> getUnresolvedPlugins() throws IOException;

	void install(PluginProgressMonitor progress, boolean forceStart, PluginComponentId... ids) throws IOException;

	List<String> getPluginInclude();

	List<String> getPluginExclude();

	void install(PluginProgressMonitor progress, boolean optional, boolean failOnError, boolean startAutostarts,
			boolean forceStart, PluginComponentId... ids) throws IOException;

	boolean isCleanOrphans();

	void cleanOrphans(PluginProgressMonitor progress);

	boolean isStarted();
	
	void setInstallMode(InstallMode installMode);
	
	InstallMode getInstallMode();

	Set<PluginArchive> list(PluginProgressMonitor progress) throws IOException;

	Set<PluginArchive> list(PluginRemote remote, PluginProgressMonitor monitor) throws IOException;

	void removeRemote(PluginRemote remote) throws IOException;

	void resolve(PluginProgressMonitor progress) throws IOException;

	void resolve(PluginProgressMonitor progress, boolean optional, boolean failOnError, boolean download,
			boolean startAutostarts, boolean forceStart) throws IOException;

	void setCleanOrphans(boolean cleanOrphans);

	void setConflictStrategy(ConflictStrategy conflictStrategy);

	void setLocal(File local);

	void start(PluginComponentId id, PluginProgressMonitor progress, String... args) throws IOException;

	void start(PluginProgressMonitor progress, String... args) throws IOException;

	void start(PluginSpec plugin, PluginProgressMonitor progress, String... args) throws IOException;

	void start(String id, PluginProgressMonitor progress, String... args) throws IOException;

	boolean isStarted(PluginComponentId spec);

	Set<PluginSpec> getStartedPlugins();

	Set<PluginSpec> getStoppedPlugins();

	Set<PluginSpec> getPlugins();

	boolean isAutostart();

	void setAutostart(boolean autoStart);

	boolean isTraces();

}