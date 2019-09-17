package com.sshtools.forker.plugin;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.sshtools.forker.plugin.api.ArchiveType;
import com.sshtools.forker.plugin.api.Plugin;
import com.sshtools.forker.plugin.api.PluginArchive;
import com.sshtools.forker.plugin.api.PluginComponentId;
import com.sshtools.forker.plugin.api.PluginDependencyTree;
import com.sshtools.forker.plugin.api.PluginManager;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;
import com.sshtools.forker.plugin.api.ResolutionState;

public class FeatureArchive extends AbstractArchiveWithDependencies {

	private boolean depsLoaded = false;
	private boolean remote;

	public FeatureArchive(PluginManager manager, URL archive, PluginProgressMonitor monitor, boolean remote)
			throws IOException {
		super(manager, archive);
		this.remote = remote;
	}

	@Override
	protected ResolutionState calcState() {
		ResolutionState base = calcBaseState();
		if (base.isComplete()) {
			base = calcArchiveState();
			if (base.isComplete()) {
				base = calcFeatureState();
				if (base.isComplete()) {
					base = calcNodeState();
				}
			}
		}
		return base;
	}

	@Override
	protected void doResolve(PluginProgressMonitor progress) throws IOException {
		if (!calcArchiveState().isResolved())
			resolveArchive(progress);

		if (!calcFeatureState().isResolved())
			resolveFeature(progress);

		if (!calcNodeState().isComplete())
			resolveNode(progress);

	}

	protected ResolutionState calcFeatureState() {
		if (depsLoaded) {
			return ResolutionState.RESOLVED;
		} else
			return ResolutionState.UNRESOLVED;
	}

	protected void resolveFeature(PluginProgressMonitor monitor) throws IOException {

		URL archive = getArchive();
		DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
		PluginDependencyTree depTree = getManager().getResolutionContext().getDependencyTree();
		try {
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			URLConnection featureConx = archive.openConnection();
			InputStream featureInputStream = featureConx.getInputStream();
			if (featureInputStream != null) {
				try {
					Document document = documentBuilder.parse(featureInputStream);
					Element root = document.getDocumentElement();

					/* Feature itself */
					PluginComponentId featureCid = new PluginComponentId(root.getAttribute("id")).withType(ArchiveType.FEATURE);
					setHash(root.getAttribute("hash"));
					setComponent(featureCid);

					NodeList featureNodes = root.getChildNodes();
					for (int h = 0; h < featureNodes.getLength(); h++) {
						Node featureNode = featureNodes.item(h);
						if (featureNode.getNodeName().equals("archives")) {

							/* Archives in the feature */
							NodeList archives = featureNode.getChildNodes();
							for (int i = 0; i < archives.getLength(); i++) {
								Node arcnode = archives.item(i);
								if (arcnode instanceof Element) {
									Element arcel = (Element) arcnode;
									PluginComponentId arcid = new PluginComponentId(arcel.getAttribute("id"));

									PluginArchive arc = depTree.get(arcid, PluginArchive.class);
									if (arc == null) {
										arc = new DefaultArchive(getManager(),
												new URL(getArchive(), arcid.toFilename() + ".jar"), arcid, remote);
										arc.setSize(Long.parseLong(arcel.getAttribute("size")));
										arc.setHash(arcel.getAttribute("hash"));

										/* Plugins in the archive */
										NodeList plugins = arcnode.getChildNodes();
										for (int j = 0; j < plugins.getLength(); j++) {
											Node pnode = plugins.item(j);
											if (pnode instanceof Element) {
												Element pluginel = (Element) pnode;
												String pluginClassname = pluginel.getAttribute("class");
												if (StringUtils.isBlank(pluginClassname))
													throw new IOException("Missing 'class' attribute in <plugin> tag.");
												PluginComponentId pluginid = new PluginComponentId(arcid,
														pluginClassname);

												List<String> dependencyNames = new ArrayList<>();
												NodeList plugindeps = pluginel.getChildNodes();
												for (int k = 0; k < plugindeps.getLength(); k++) {
													Node pdnode = plugindeps.item(k);
													if (pdnode.getNodeName().equals("dependency"))
														dependencyNames.add(pdnode.getTextContent().trim());
												}

												DefaultPluginSpec spec = new DefaultPluginSpec(arc, pluginid,
														dependencyNames.toArray(new String[0]),
														pluginel.getAttribute("name"),
														pluginel.getAttribute("description"));

												String start = pluginel.getAttribute("start");
												if (start != null) {
													switch (Plugin.StartMode.valueOf(start)) {
													case AUTO:
														spec.setAutostart(true);
														break;
													case MANUAL:
														spec.setAutostart(false);
														break;
													case DEFAULT:
														spec.setAutostart(getParent().isAutostart());
														break;
													}
												} else {
													spec.setAutostart(getParent().isAutostart());
												}
												arc.addChild(spec);
											}
										}

										depTree.add(arc, monitor);
									}
									addDependency(arcid);
									arc.addDependent(featureCid);
								}
							}
						}
					}
					depsLoaded = true;
				} finally {
					featureInputStream.close();
				}
			} else
				throw new FileNotFoundException(String.format("Feature descriptor %s not found.", archive));
		} catch (ParserConfigurationException pce) {
			throw new IOException("Could not configure XML parser. ", pce);
		} catch (SAXException e) {
			throw new IOException("Could not parse XML. ", e);
		}
	}

}
