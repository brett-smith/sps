package com.sshtools.forker.plugin.maven;

import java.util.Map;

import org.apache.maven.model.Model;

import com.sshtools.forker.plugin.PropertyResolver;

public class MavenPropertyResolver extends PropertyResolver {

	private static final long serialVersionUID = 1L;

	public MavenPropertyResolver(Model model) {
		super(model.getProperties());

		if (model.getGroupId() == null && model.getParent() != null)
			putIfNotNull("project.groupId", model.getParent().getGroupId());
		else
			putIfNotNull("project.groupId", model.getGroupId());
		putIfNotNull("project.artifactId", model.getArtifactId());
		if (model.getVersion() == null && model.getParent() != null)
			putIfNotNull("project.version", model.getParent().getVersion());
		else
			putIfNotNull("project.version", model.getVersion());
		putIfNotNull("project.name", model.getName());
		putIfNotNull("project.description", model.getDescription());

		putAll(System.getProperties());

		for (Map.Entry<String, String> en : System.getenv().entrySet()) {
			put("env." + en.getKey(), en.getValue());
		}
	}

}
