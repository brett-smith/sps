package com.sshtools.forker.plugin.maven;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import com.sshtools.forker.plugin.api.ConflictStrategy;
import com.sshtools.forker.plugin.api.PluginArchive;
import com.sshtools.forker.plugin.api.PluginComponentId;
import com.sshtools.forker.plugin.api.PluginDependencyTree;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;
import com.sshtools.forker.plugin.api.PluginResolveContext;

public class POMReader {

	private boolean resolveDependencies;
	private boolean embedded;

	public boolean embedded() {
		return embedded;
	}

	public POMReader embedded(boolean embedded) {
		this.embedded = embedded;
		return this;
	}

	public boolean resolveDependencies() {
		return resolveDependencies;
	}

	public POMReader resolveDependencies(boolean resolveDependencies) {
		this.resolveDependencies = resolveDependencies;
		return this;
	}

	public void readPOM(PluginArchive parent, InputStream inStream, PluginProgressMonitor monitor) throws IOException {
		MavenXpp3Reader reader = new MavenXpp3Reader();
		PluginResolveContext ctx = parent.getParent().getResolutionContext();
		ConflictStrategy strategy = ctx.conflictStrategy();
		if(strategy == null)
			strategy = parent.getParent().getConflictStrategy();
		
		PluginDependencyTree depTree = ctx.getDependencyTree();
		try (InputStreamReader in = new InputStreamReader(inStream)) {
			try {
				Model model = reader.read(in);
				MavenPropertyResolver resolver = new MavenPropertyResolver(model);
				String artifactId = resolver.process(model.getArtifactId());
				
				String groupId = resolver.process(model.getGroupId());
				String version = resolver.process(model.getVersion());
				parent.setName(model.getName());
				parent.setDescription(model.getDescription());

				if (groupId == null && model.getParent() != null)
					groupId = resolver.process(model.getParent().getGroupId());

				if (version == null && model.getParent() != null)
					version = resolver.process(model.getParent().getVersion());

				parent.setComponent(new PluginComponentId(groupId, artifactId, version));

				if (resolveDependencies) {
					for (Dependency dep : model.getDependencies()) {
						/* Skip test dependencies for now */
						if (!"test".equals(dep.getScope())) {

							/* First see if we already have it */
							String depGroupId = resolver.process(dep.getGroupId());
							String depArtifactId = resolver.process(dep.getArtifactId());
							String depVersion = resolver.process(dep.getVersion());
							
							if(depArtifactId.equals("log4j")) {
								String x;
								x = "";
							}
							if(PluginComponentId.isVariablePattern(depVersion)) {
								depVersion = "";
							}

							PluginComponentId key = new PluginComponentId(depGroupId, depArtifactId, depVersion);
							
							/* If the dependency version is not valid at this point, it is likely due
							 * to the version being specified as a property that is not resolvable.
							 * 
							 * Assume it to be any version
							 */
							if(!key.isValid()) {
								key = key.withoutVersion();
							}
							
							if (key.isValid()) {

								PluginArchive mpa = depTree.get(key, PluginArchive.class);
								if(mpa == null && key.hasVersion()) {
									if(strategy == ConflictStrategy.USE_EARLIEST || strategy == ConflictStrategy.USE_LATEST)
										mpa = depTree.get(key.withoutVersion(), PluginArchive.class);
								}
								
								if (mpa == null) {
									if (embedded) {
										try {
											mpa = new MavenJarArchive(parent,
													new PluginComponentId(depGroupId, depArtifactId, depVersion)
															.asOptional(dep.isOptional()), monitor);
										}
										catch(IllegalArgumentException iae) {
											// Nothing can be done
										}
									}
									else {
										mpa = new MavenJarArchive(parent.getParent(),
												new PluginComponentId(depGroupId, depArtifactId, depVersion)
														.asOptional(dep.isOptional()));
									}
									
									if(mpa != null) {
										parent.addDependency(mpa.getComponentId());
										depTree.add(mpa, monitor);
									}
								} else {
									if(!mpa.getComponentId().isOptional() && dep.isOptional()) {
										ctx.getDependencyTree().remove(mpa);
										mpa.setComponent(mpa.getComponentId().asOptional(true));
										ctx.getDependencyTree().add(mpa, monitor);
									}
									parent.addDependency(mpa.getComponentId());
								}
								
								if (parent != null && mpa != null)
									mpa.addDependent(parent.getComponentId());
							}
						}
					}
				}
				return;
			} catch (XmlPullParserException e) {
				throw new IOException("Could not read POM.", e);
			}
		}
	}
}
