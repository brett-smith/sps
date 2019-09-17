package com.sshtools.forker.plugin.maven.plugin;

import org.apache.maven.plugins.annotations.Parameter;

public class FeatureConfig {

	/**
	 * Feature name.
	 */
	@Parameter(defaultValue = "${project.name}")
	String name;

	/**
	 * Required features.
	 */
	@Parameter
	String[] requiredFeatures;

}
