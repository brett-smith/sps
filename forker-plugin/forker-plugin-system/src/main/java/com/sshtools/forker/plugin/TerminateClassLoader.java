package com.sshtools.forker.plugin;

public class TerminateClassLoader extends ClassLoader {

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		throw new ClassNotFoundException(name);
	}

}
