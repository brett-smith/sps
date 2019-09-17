package com.sshtools.forker.plugin.api;

import java.net.URL;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class PluginComponentId implements Comparable<PluginComponentId> {

	private final String group;
	private final String id;
	private final String version;
	private final boolean optional;
	private final String plugin;
	private final ArchiveType type;

	public PluginComponentId(String spec) {
		String[] a = spec.split("@");
		if (a.length > 1) {
			group = a[0];
			id = a[1];
			if (a.length > 2) {
				version = parseVersionForVersion(a[2]);
				optional = parseVersionForOptional(a[2]);
				if (a.length > 3) {
					plugin = parsePluginForPlugin(a[3]);
					type = parsePluginForType(a[3]);
				} else {
					plugin = null;
					type = ArchiveType.JAR;
				}
			} else {
				version = null;
				optional = false;
				type = ArchiveType.JAR;
				plugin = null;
			}
		} else {
			group = null;
			id = null;
			version = null;
			optional = false;
			plugin = spec;
			type = ArchiveType.PLUGIN;
		}
		check();
	}

	protected void check() {
		if(plugin != null && plugin.length() > 0 && type != ArchiveType.PLUGIN) {
			throw new UnsupportedOperationException("Bad KEY " + toString());
		}
		if(version != null && version.contains("${"))
			throw new UnsupportedOperationException("Bad KEY " + toString());
	}

	public PluginComponentId(String group, String id) {
		this(group, id, null);
	}

	public PluginComponentId(String group, String id, String version) {
		this(group, id, version, null);
	}

	public PluginComponentId(String group, String id, String version, String plugin) {
		this(group, id, version, parsePluginForPlugin(plugin), parsePluginForType(plugin));
	}

	public PluginComponentId(String group, String id, String version, String plugin, ArchiveType type) {
		super();
		this.group = group;
		this.id = id;
		this.version = parseVersionForVersion(version);
		this.optional = parseVersionForOptional(version);
		this.plugin = plugin;
		if ((plugin != null && plugin.length() > 0) && type != ArchiveType.PLUGIN)
			throw new IllegalArgumentException(
					String.format("This component ID %s@%s@%s that supplies a plugin name must be of type %s.", group,
							id, version, ArchiveType.PLUGIN));
		this.type = type;
		check();
	}

	private static boolean parseVersionForOptional(String version) {
		return version != null && version.startsWith("~");
	}

	private static String parseVersionForVersion(String version) {
		if (version != null && version.startsWith("~"))
			version = version.length() == 1 ? null : version.substring(1);
		return version;
	}

	public PluginComponentId(PluginComponentId spec, String relSpec) {
		String iid = spec.getId();
		String igroup = spec.getGroup();
		String iversion = spec.getVersion();
		boolean ioptional = spec.isOptional();
		String iplugin = spec.getPlugin();
		ArchiveType itype = spec.getType();
		String[] parts = relSpec.split("@");
		if (parts.length == 1) {
			if (relSpec.length() > 0 && (relSpec.indexOf(".") != -1 || Character.isUpperCase(relSpec.charAt(0)))) {
				if (Character.isUpperCase(relSpec.charAt(0))) {
//					if (!spec.hasPlugin())
//						throw new IllegalArgumentException(
//								"Relative spec has a relative class name, but parent spec is not a plugin.");
//					int idx = spec.getVersion().lastIndexOf(".");
//					iplugin = (idx == -1 ? "" : spec.getVersion().substring(0, idx + 1)) + relSpec;

					if (!spec.hasPlugin()) {
						iplugin = relSpec;
						itype = ArchiveType.PLUGIN; 
					} else {
						int idx = spec.getVersion().lastIndexOf(".");
						iplugin = (idx == -1 ? "" : spec.getVersion().substring(0, idx + 1)) + relSpec;
						itype = ArchiveType.PLUGIN; 
					}

				} else {
					iplugin = parts[0];
					itype = ArchiveType.PLUGIN; 
				}
			} else
				iid = parts[0];
		} else if (parts.length == 2) {
			igroup = parts[0];
			iid = parts[1];
		} else if (parts.length == 3) {
			igroup = parts[0];
			iid = parts[1];
			iversion = parseVersionForVersion(parts[2]);
			ioptional = parseVersionForOptional(parts[2]);
		} else if (parts.length == 4) {
			igroup = parts[0];
			iid = parts[1];
			iversion = parseVersionForVersion(parts[2]);
			ioptional = parseVersionForOptional(parts[2]);
			iplugin = parsePluginForPlugin(parts[3]);
			itype = parsePluginForType(parts[3]);
		} else
			throw new IllegalArgumentException(String.format("Invalid Relative Component ID %s", relSpec));
		id = iid;
		group = igroup;
		version = iversion;
		optional = ioptional;
		plugin = iplugin;
		type = itype;
		check();
	}

	private static ArchiveType parsePluginForType(String string) {
		for (ArchiveType t : ArchiveType.values()) {
			if (t.name().equals(string))
				return t;
		}
		return string == null || string.length() == 0 ? ArchiveType.JAR : ArchiveType.PLUGIN;
	}

	private static String parsePluginForPlugin(String string) {
		if (string == null || string.length() == 0)
			return string;
		for (ArchiveType t : ArchiveType.values()) {
			if (t.name().equals(string))
				return null;
		}
		return string;
	}

	public ArchiveType getType() {
		return type;
	}

	public boolean onlyHasPlugin() {
		return plugin != null && plugin.length() > 0 && (group == null || group.length() == 0)
				&& (version == null || version.length() == 0) && (id == null || id.length() == 0);
	}

	public String getPlugin() {
		return plugin;
	}

	public boolean isOptional() {
		return optional;
	}

	public boolean hasVersion() {
		return version != null && version.length() > 0;
	}

	public String toString() {
		String s = group + "@" + id;
		if (hasVersion() || hasPlugin())
			s += "@";
		if (optional)
			s += "~";
		s += (version == null ? "" : version);
		if (plugin != null)
			s += "@" + plugin;
		else if (type != ArchiveType.JAR)
			s += "@" + type;
		return s;
	}

	public String toFilename() {
		String s = group + "@" + id + "@" + (version == null ? "" : version);
		if (plugin != null)
			s += "@" + plugin;
		else if (type != ArchiveType.JAR)
			s += "@" + type;
		return s;
	}

	public String getGroup() {
		return group;
	}

	public String getId() {
		return id;
	}

	public String getVersion() {
		return version;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((group == null) ? 0 : group.hashCode());
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + (optional ? 1231 : 1237);
		result = prime * result + ((plugin == null) ? 0 : plugin.hashCode());
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		result = prime * result + ((version == null) ? 0 : version.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PluginComponentId other = (PluginComponentId) obj;
		if (group == null) {
			if (other.group != null)
				return false;
		} else if (!group.equals(other.group))
			return false;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		if (optional != other.optional)
			return false;
		if (plugin == null) {
			if (other.plugin != null)
				return false;
		} else if (!plugin.equals(other.plugin))
			return false;
		if (type != other.type)
			return false;
		if (version == null) {
			if (other.version != null)
				return false;
		} else if (!version.equals(other.version))
			return false;
		return true;
	}

	public PluginComponentId withoutPlugin() {
		return new PluginComponentId(group, id, version, null, ArchiveType.JAR);
	}

	public PluginComponentId withoutVersion() {
		return new PluginComponentId(group, id, null, plugin, type);
	}

	public PluginComponentId withVersion(String version) {
		return new PluginComponentId(group, id, version, plugin, type);
	}

	public PluginComponentId withType(ArchiveType type) {
		return new PluginComponentId(group, id, version, plugin, type);
	}

	public PluginComponentId withPlugin(String plugin) {
		return new PluginComponentId(group, id, version, plugin, ArchiveType.PLUGIN);
	}

	public PluginComponentId withGroup(String group) {
		return new PluginComponentId(group, id, version, plugin, type);
	}

	public PluginComponentId withId(String id) {
		return new PluginComponentId(group, id, version, plugin, type);
	}

	public PluginComponentId asOptional(boolean optional) {
		return new PluginComponentId(group, id, optional ? "~" + (version == null ? "" : version) : version, plugin);
	}

	public static PluginComponentId resolvePluginId(PluginComponentId thisId, String dep) {
		PluginComponentId pci;
		String thisPlugin = thisId.getPlugin();
		if (Character.isUpperCase(dep.charAt(0)) && dep.indexOf('@') == -1 && dep.indexOf('.') == -1) {
			/*
			 * First character is upper case, so the dependency expresses a relative plugin
			 * classname (relative to this one)
			 */
			int idx = thisPlugin.lastIndexOf('.');
			String base = "";
			if (idx != -1)
				base = thisPlugin.substring(0, idx + 1);
			pci = thisId.withPlugin(base + dep);
		} else if (dep.indexOf('@') == -1 && dep.indexOf('.') != -1) {
			/* Fully qualified plugin class name */
			pci = thisId.withPlugin(dep);
		} else {
			pci = new PluginComponentId(thisId, dep);
		}
		return pci;
	}

	public boolean hasPlugin() {
		return plugin != null && plugin.length() > 0;
	}

	public static Set<String> toString(Collection<PluginComponentId> names) {
		Set<String> l = new LinkedHashSet<>();
		for (PluginComponentId pan : names)
			l.add(pan.toString());
		return l;
	}

	public boolean isComplete() {
		return id != null && group != null && versionIsValid(version);
	}

	public boolean isValid() {
		return id != null && group != null && (version == null || version.length() == 0 || versionIsValid(version));
	}

	private boolean versionIsValid(String version) {
		if (version == null || version.length() == 0)
			return false;
		return !isVariablePattern(version);
	}

	public boolean isNamed() {
		return id != null && group != null;
	}

	public PluginComponentId idAndGroup() {
		return new PluginComponentId(group, id, null, null);
	}

	public static PluginComponentId fromURL(URL archive) {
		String path = archive.getPath();
		if (path == null)
			throw new IllegalArgumentException("Invalid URL. Cannot extract component ID.");
		while (path.endsWith("/"))
			path = path.substring(0, path.length() - 1);
		String base = path;
		String dir = path;
		int idx = base.lastIndexOf('/');
		if (idx != -1) {
			dir = base.substring(0, idx);
			base = base.substring(idx + 1);
		} else {
			dir = "global";
		}
		String ext = "jar";
		idx = base.lastIndexOf('.');
		if (idx != -1) {
			ext = base.substring(idx + 1);
			base = base.substring(0, idx);
		}
		if (base.indexOf('@') != -1 && ext.equals("jar") || ext.equals("xml")) {
			return new PluginComponentId(base);
		} else {
			return new PluginComponentId(dir.replace('/', '.'), base);
		}
	}

	@Override
	public int compareTo(PluginComponentId o) {
		return toString().compareTo(o.toString());
	}


	public static boolean isVariablePattern(String txt) {
		if (txt == null || txt.length() == 0)
			return false;
		return txt.matches("\\$\\{[a-zA-Z\\d\\.\\-\\_]*\\}");
	}
}
