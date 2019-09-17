package com.sshtools.forker.plugin;

import java.util.LinkedHashSet;
import java.util.Set;

public class ClassesMissingException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	private Set<String> classNames = new LinkedHashSet<>();

	public ClassesMissingException(Set<String> classNames) {
		this.classNames = classNames;
	}

	public Set<String> getClassNames() {
		return classNames;
	}
}
