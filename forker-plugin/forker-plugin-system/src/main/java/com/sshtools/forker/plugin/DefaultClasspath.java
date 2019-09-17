package com.sshtools.forker.plugin;

import java.io.File;
import java.io.FileFilter;

import org.apache.commons.lang3.StringUtils;

import com.sshtools.forker.plugin.api.PluginClasspath;
import com.sshtools.forker.plugin.api.PluginManager;

public class DefaultClasspath implements PluginClasspath {

	private String archives;
	private PluginManager manager;

	{
		archives = System.getProperty("sps.archives", System.getenv("SPS_ARCHIVES"));
		
//		System.out.println("EXTD: " +System.getProperty("java.ext.dirs"));
//		System.out.println("BCP: " +System.getProperty("sun.boot.class.path"));
//		System.out.println("BLP: " +System.getProperty("sun.boot.library.path"));
//		System.out.println("LP: " +System.getProperty("java.library.path"));
//		System.out.println("JH: " +System.getProperty("java.home"));
//		System.out.println("ED: " +System.getProperty("java.endorsed.dir"));
	}

	public DefaultClasspath(PluginManager manager) {
		this.manager = manager;
	}

	public String getArchives() {
		return archives;
	}

	@Override
	public String[] getPath() {
		String path = System.getProperty("java.class.path");
		if (StringUtils.isNotBlank(getArchives())) {
			if (StringUtils.isNotBlank(path))
				path = path + File.pathSeparator + getArchives();
			else
				path = getArchives();
		}
		if (manager.getLocal().exists()) {
			for (File jar : manager.getLocal().listFiles(new FileFilter() {
				@Override
				public boolean accept(File pathname) {
					String ln = pathname.getName().toLowerCase();
					return pathname.isDirectory() || ln.endsWith(".jar") || ln.endsWith(".xml");
				}
			})) {
				if (StringUtils.isNotBlank(path))
					path = path + File.pathSeparator + jar.getPath();
				else
					path = jar.getPath();
			}
		}
		return path.equals("") ? new String[0] : path.split(File.pathSeparator);
	}

	public void setArchives(String archives) {
		this.archives = archives;
	}
}
