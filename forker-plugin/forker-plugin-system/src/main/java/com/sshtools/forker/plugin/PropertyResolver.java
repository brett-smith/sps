package com.sshtools.forker.plugin;

import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PropertyResolver extends Properties {

	private static final long serialVersionUID = 1L;
	private List<String> unresolvedVariables = new LinkedList<>();
	private Pattern varPattern;

	public PropertyResolver() {
		varPattern = Pattern.compile("\\$\\{([a-zA-Z\\d\\.-_]*)\\}", Pattern.MULTILINE);
	}

	public PropertyResolver(Properties properties) {
		this();
		putAll(properties);
	}

	public String process(String text) {
		if (text == null)
			return text;
		if(text.indexOf("${") == -1) 
			return text;
		Matcher m = varPattern.matcher(text);
		StringBuffer sb = new StringBuffer(text.length());
		while (m.find()) {
			String var = m.group(1);
			if (containsKey(var)) {
				String val = getProperty(var);
				m.appendReplacement(sb, val == null ? "" : Matcher.quoteReplacement(val));
			} else if (!unresolvedVariables.contains(var))
				unresolvedVariables.add(var);
		}
		m.appendTail(sb);
		return sb.toString();
	}

	public void putIfNotNull(String key, String val) {
		if (val != null)
			put(key, val);
	}

}
