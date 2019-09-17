package com.sshtools.forker.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.sshtools.forker.plugin.api.PluginArchive;
import com.sshtools.forker.plugin.api.PluginComponentId;

public class PluginClassLoader extends URLClassLoader {

	static boolean DBG = false;

	private ClassLoader system;
	private PluginArchive archive;

	public PluginClassLoader(PluginArchive archive, ClassLoader parent) {
		super(archive.getClasspath().toArray(new URL[0]), parent);
		system = getSystemClassLoader();
		this.archive = archive;
	}

	@Override
	public String toString() {
		return "PluginClassLoader [archive=" + archive + "]";
	}

	@Override
	public synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		if (DBG)
			System.out.println("loadClass " + name);
		// First, check if the class has already been loaded
		try {
			Class<?> c = findLoadedClass(name);
			if (c == null) {
				if (system != null) {
					try {
						c = system.loadClass(name);
						if (DBG)
							System.out.println("   " + name + " found in system");
					} catch (ClassNotFoundException ignored) {
					}
				}
				if (c == null) {
					try {
						// checking local
						c = findClass(name);
						if (DBG)
							System.out.println("   " + name + " found in local");
					} catch (ClassNotFoundException e) {
						for (PluginComponentId id : archive.getDependencies()) {
							PluginArchive parc = archive.getParent().getResolutionContext().getDependencyTree().get(id,
									PluginArchive.class);
							if (parc != null) {
								try {
									ClassLoader classLoader = parc.getClassLoader();
									if (DBG)
										System.out.println("   looking " + name + " in " + id);
									// if (classLoader != system && classLoader != this && classLoader !=
									// getParent())
									if (classLoader != this && classLoader != system) {
										c = classLoader.loadClass(name);
										if (c == null)
											System.out.println("   NULL CLASS!");
										else if (DBG)
											System.out.println("   found " + name + " in " + id + " / " + c.toString());
										break;
									}
								} catch (ClassNotFoundException cnfe) {
								}
							} else if (DBG)
								System.out.println("  no dep " + id + "!");
						}

						// checking parent
						// This call to loadClass may eventually call findClass again, in case the
						// parent doesn't find anything.
						if (c == null && getParent() != system) {
							if (DBG)
								System.out.println("  looking in parent");
							c = super.loadClass(name);
						}
					}
				}
			} else if (DBG)
				System.out.println("  iscached " + name);

			if (c == null)
				throw new ClassNotFoundException(name);

			if (resolve) {
				resolveClass(c);
			}
			if (DBG)
				System.out.println("  found " + name);
			return c;
		} catch (ClassNotFoundException cnfe) {
			if (DBG)
				System.out.println("Not found: " + name);
			throw cnfe;
		}
	}

	@Override
	public URL getResource(String name) {
		if (DBG)
			System.out.println("getResource " + name);
		URL url = null;
		if (system != null) {
			if (DBG)
				System.out.println("   trying system " + name);
			url = system.getResource(name);
		}
		if (url == null) {
			if (DBG)
				System.out.println("   trying local " + name);
			url = findResource(name);

			if (url == null) {

				for (PluginComponentId id : archive.getDependencies()) {
					PluginArchive parc = archive.getParent().getResolutionContext().getDependencyTree().get(id,
							PluginArchive.class);
					if (parc != null) {
						ClassLoader classLoader = parc.getClassLoader();
						if (classLoader != this && classLoader != system) {
							if (DBG)
								System.out.println("   trying " + id + " for " + name);
							url = classLoader.getResource(name);
							break;
						}
					}
				}

				// This call to getResource may eventually call findResource again, in case the
				// parent doesn't find anything.
				if (url == null)
					url = super.getResource(name);
			}
		}
		if (DBG)
			System.out.println("   found at " + url);
		return url;
	}

	@Override
	public Enumeration<URL> getResources(String name) throws IOException {
		if (DBG)
			System.out.println("getResources " + name);
		/**
		 * Similar to super, but local resources are enumerated before parent resources
		 */
		Enumeration<URL> systemUrls = null;
		if (system != null) {
			systemUrls = system.getResources(name);
		}
		Enumeration<URL> localUrls = findResources(name);
		Enumeration<URL> parentUrls = null;
		List<Enumeration<URL>> depUrls = new LinkedList<>();

		for (PluginComponentId id : archive.getDependencies()) {
			PluginArchive parc = archive.getParent().getResolutionContext().getDependencyTree().get(id,
					PluginArchive.class);
			if (parc != null) {
				ClassLoader classLoader = parc.getClassLoader();
				if (classLoader != this && classLoader != system) {
					Enumeration<URL> e = classLoader.getResources(name);
					if (e != null && e.hasMoreElements())
						depUrls.add(e);
				}
			}
		}

		if (getParent() != null) {
			parentUrls = getParent().getResources(name);
		}
		final List<URL> urls = new ArrayList<URL>();
		if (systemUrls != null) {
			while (systemUrls.hasMoreElements()) {
				urls.add(systemUrls.nextElement());
			}
		}
		if (localUrls != null) {
			while (localUrls.hasMoreElements()) {
				urls.add(localUrls.nextElement());
			}
		}

		for (Enumeration<URL> en : depUrls) {
			while (en.hasMoreElements()) {
				urls.add(en.nextElement());
			}
		}

		if (parentUrls != null) {
			while (parentUrls.hasMoreElements()) {
				urls.add(parentUrls.nextElement());
			}
		}
		if (DBG)
			System.out.println("getResources found " + urls.size() + " for " + name);
		return new Enumeration<URL>() {
			Iterator<URL> iter = urls.iterator();

			public boolean hasMoreElements() {
				return iter.hasNext();
			}

			public URL nextElement() {
				return iter.next();
			}
		};
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		URL url = getResource(name);
		try {
			return url != null ? url.openStream() : null;
		} catch (IOException e) {
		}
		return null;
	}

}