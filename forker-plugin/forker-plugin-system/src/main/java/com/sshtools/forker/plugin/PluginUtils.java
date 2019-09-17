package com.sshtools.forker.plugin;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.Set;

import com.sshtools.forker.plugin.api.PluginComponent;
import com.sshtools.forker.plugin.api.PluginComponentId;

public class PluginUtils {
	
	static String spaces = "";
	
	static {
		for(int i = 0 ; i < 1024; i++)
			spaces+= " ";
	}

	public static String stripTrailingSlash(String path) {
		while (!path.equals("/") && path.endsWith("/w"))
			path = path.substring(0, path.length() - 1);
		return path;
	}

	public static boolean isDescendant(File base, File child) throws IOException {
		base = base.getAbsoluteFile();
		child = child.getAbsoluteFile();

		File parentFile = child;
		while (parentFile != null) {
			if (base.equals(parentFile)) {
				return true;
			}
			parentFile = parentFile.getParentFile();
		}
		return false;
	}

	public static File urlToFile(URL url) {
		if (url.getProtocol().equals("file")) {
			return new File(stripTrailingSlash(url.getPath())).getAbsoluteFile();
		} else
			throw new IllegalArgumentException();

	}

	public static String getPathRelativeToCwd(URL part) {
		if (part == null)
			return null;
		String path = part.getPath();
		if (part.getProtocol().equals("file")) {
			File thisDir = new File(System.getProperty("user.dir"));
			if (path.startsWith(thisDir.getAbsolutePath()))
				path = path.substring(thisDir.getAbsolutePath().length() + 1);
		}
		return path;
	}

	public static Set<PluginComponentId> toIds(Set<? extends PluginComponent<?>> components) {
		Set<PluginComponentId> l = new LinkedHashSet<>();
		for (PluginComponent<?> c : components)
			l.add(c.getComponentId());
		return l;
	}

	public static String trimFileName(File file) {
		String path = new File(System.getProperty("user.dir")).getAbsolutePath();
		return file.getAbsolutePath().startsWith(path) ? file.getAbsolutePath().substring(path.length() + 1) : file.getPath();
	}

	public static String spaces(int len) {
		return spaces.substring(0, len);
	}

	public static String trimUrl(URL url) {
		try {
			return trimFileName(urlToFile(url));
		}
		catch(IllegalArgumentException iae) {
			return url.toString();
		}
	}

	public static File mostRecentlyModified(File f1, File f2) {
		return f1.lastModified() > f2.lastModified() ? f1 : f2;
	}

}
