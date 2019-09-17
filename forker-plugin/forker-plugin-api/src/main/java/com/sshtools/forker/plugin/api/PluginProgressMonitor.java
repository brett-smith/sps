package com.sshtools.forker.plugin.api;

public interface PluginProgressMonitor {

	public enum MessageType {
		INFO, WARNING, ERROR, DEBUG
	}

	default boolean isCancelled() {
		return false;
	}

	default void start(long totalTasks) {

	}

	default void end() {
	}

	default void progress(long tasks, String message) {

	}

	void message(MessageType type, String message);

	default void changeTotal(long newTotalTasks) {
	}
}
