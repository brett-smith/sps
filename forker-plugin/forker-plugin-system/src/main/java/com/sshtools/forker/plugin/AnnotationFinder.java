package com.sshtools.forker.plugin;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.sshtools.forker.plugin.api.Plugin;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;
import com.sshtools.forker.plugin.api.PluginProgressMonitor.MessageType;

public abstract class AnnotationFinder {

	private ClassLoader classLoader;
	private Collection<URL> classpath;
	private boolean stopOnClassNotFound = false;
	private boolean stopOnOtherErrors = false;
	private Set<String> ignores = new HashSet<>();

	public AnnotationFinder(Collection<URL> classpath) {
		this(Thread.currentThread().getContextClassLoader(), classpath);
	}

	public AnnotationFinder(ClassLoader classLoader, Collection<URL> classpath) {
		this.classLoader = classLoader;
		this.classpath = classpath;
	}

	public AnnotationFinder(ClassLoader classLoader, URL... classpath) {
		this(classLoader, Arrays.asList(classpath));
	}

	public Set<String> getIgnores() {
		return ignores;
	}

	public Set<String> find(PluginProgressMonitor monitor) throws ClassNotFoundException {
		Set<String> missing = new HashSet<>();
		for (URL url : this.classpath) {
			if (url.getProtocol().equals("file")) {
				File file = new File(url.getPath());
				try {
					if (file.isDirectory())
						scanDir(missing, file, file, monitor);
					else if (file.getName().toLowerCase().endsWith(".jar")
							|| file.getName().toLowerCase().endsWith(".zip")) {
						try (ZipFile zipfile = new ZipFile(file)) {
							Enumeration<? extends ZipEntry> entries = zipfile.entries();
							while (entries.hasMoreElements()) {
								ZipEntry zipEntry = entries.nextElement();
								if (zipEntry.getName().endsWith(".class")) {
									String cp = zipEntry.getName().replace('/', '.');
									cp = cp.substring(0, cp.length() - 6);
									if (!ignores.contains(cp)) {
										try {
											Class<?> clazz = classLoader.loadClass(cp);
											Plugin plugin = clazz.getAnnotation(Plugin.class);
											if (plugin != null)
												onPlugin(clazz, plugin);
										} catch (ClassNotFoundException e) {
											missing.add(cp);
											if (stopOnClassNotFound) {
												throw new ClassNotFoundException(cp);
											}
											if (monitor != null)
												monitor.message(MessageType.DEBUG, String
														.format("Cannot locate class %s to inspect annotations.", cp));
										} catch (SecurityException | LinkageError | IllegalArgumentException ucve) {
											missing.add(cp);
											if (stopOnOtherErrors)
												throw new ClassNotFoundException(cp);
											else
												monitor.message(MessageType.DEBUG,
														String.format("Cannot load %s.", cp));
										}
									}
								}
							}
						}
					} else if (file.getName().toLowerCase().endsWith(".xml")) {
						return missing;
					} else
						throw new UnsupportedOperationException(
								String.format("The archive %s cannot be scanned for annotations.", url));
				} catch (IOException ioe) {
					throw new IllegalStateException(String.format("Cannot scan archive %s.", file), ioe);
				}
			} else
				throw new UnsupportedOperationException();
		}

		return missing;
	}

	public boolean stopOnClassNotFound() {
		return stopOnClassNotFound;
	}

	public boolean stopOnOtherErrors() {
		return stopOnOtherErrors;
	}

	public AnnotationFinder stopOnClassNotFound(boolean stopOnClassNotFound) {
		this.stopOnClassNotFound = stopOnClassNotFound;
		return this;
	}

	public AnnotationFinder stopOnOtherErrors(boolean stopOnOtherErrors) {
		this.stopOnOtherErrors = stopOnOtherErrors;
		return this;
	}

	protected Plugin onClass(Class<?> clazz) throws IOException {
		Plugin plugin = clazz.getDeclaredAnnotation(Plugin.class);
		if (plugin != null)
			onPlugin(clazz, plugin);
		return plugin;
	}

	protected abstract void onPlugin(Class<?> clazz, Plugin plugin) throws IOException;

	protected void scanDir(Set<String> missing, File base, File file, PluginProgressMonitor monitor)
			throws IOException, ClassNotFoundException {
		for (File f : file.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				return pathname.isDirectory() || pathname.getName().endsWith(".class");
			}
		})) {
			if (f.isDirectory()) {
				scanDir(missing, base, f, monitor);
			} else {
				String rel = f.getPath().substring(base.getPath().length() + 1);
				String cp = rel.replace(File.separatorChar, '.');
				cp = cp.substring(0, cp.length() - 6);
				if (!ignores.contains(cp)) {
					try {
						Class<?> clazz = classLoader.loadClass(cp);
						onClass(clazz);
					} catch (ClassNotFoundException e) {
						missing.add(cp);
						if (stopOnClassNotFound) {
							throw new ClassNotFoundException(cp);
						}
						if (monitor != null)
							monitor.message(MessageType.WARNING,
									String.format("Cannot locate class %s to inspect annotations.", cp));
					} catch (NoClassDefFoundError | UnsupportedClassVersionError ucve) {
						missing.add(cp);
						if (stopOnOtherErrors)
							throw new ClassNotFoundException(cp);
						else
							monitor.message(MessageType.DEBUG, String.format("Cannot load %s.", cp));
					}
				}
			}
		}
	}

	public AnnotationFinder addIgnores(Set<String> ignores) {
		this.ignores.addAll(ignores);
		return this;
	}
}
