package mypackage;

import java.util.Stack;

import com.sshtools.forker.plugin.DefaultPluginManager;
import com.sshtools.forker.plugin.FeatureRemote;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;
import com.sshtools.forker.plugin.maven.MavenRemote;

public class TestApp {

	public static void main(String[] args) throws Exception {
		PluginProgressMonitor pp = new PluginProgressMonitor() {

			private Stack<Long> tasks = new Stack<>();
			private String spaces = "";
			private String blocks = "";

			{
				for (int i = 0; i < 100; i++) {
					spaces += " ";
					blocks += "◼";
				}
			}

			@Override
			public void start(long totalTasks) {
				tasks.push(totalTasks);
			}

			@Override
			public void end() {
				tasks.pop();
				System.out.println();
			}

			@Override
			public void progress(long progress, String message) {
				long total = this.tasks.peek();
				int pc = (int) ((double) progress / (double) total * 100d);
				int w = pc / 2;
				String txt = spaces.substring(0, 50);
				if (w != 0) {
					txt = blocks.substring(0, w) + spaces.substring(0, 50 - w);
				}
				String msg = message;
				int cw = 60;
				if (msg.length() > cw)
					msg = msg.substring(0, cw);
				if (msg.length() < cw)
					msg = msg + spaces.substring(0, cw - msg.length());
				System.out.print(String.format("%-" + cw + "s - %s [%3d%%]\r", msg, txt, pc));
			}

			@Override
			public void message(MessageType type, String message) {
				if (type != MessageType.DEBUG)
					System.out.println(type + " : " + message);
			}
		};
		try (DefaultPluginManager mgr = new DefaultPluginManager()) {
			mgr.setCleanOrphans(false);
//			mgr.addRemote(new MavenProjectRemote("../forker-plugin-example-2"));
//			mgr.addRemote(new MavenProjectRemote("../forker-plugin-example-3"));
			mgr.addRemote(new MavenRemote("http://central.maven.org/maven2/"));
			mgr.addRemote(new FeatureRemote("../forker-plugin-feature-example-1/target/feature"));
//			mgr.addRemote(new MavenRemote(
//					System.getProperty("user.home") + File.separator + ".m2" + File.separator + "repository"));

//			if (mgr.getState(pp) != ResolutionState.RESOLVED) {
//				pp.message(MessageType.INFO, "Not fully resolved :-");
//				mgr.resolve(pp);
//				ResolutionState state = mgr.getState(pp);
//				if (state == ResolutionState.NOT_RESOLVED)
//					throw new IOException("Could not fully resolve any plugins or dependencies. ");
//				else if (state != ResolutionState.RESOLVED) {
//					pp.message(MessageType.WARNING,
//							"Could not fully resolve all plugins and dependencies." + mgr.getUnresolvedArchives(pp)
//									+ ", " + mgr.getUnresolvedPlugins(pp) + ". State was " + state);
//				}
//			}
//			for (PluginArchive arc : mgr.getChildren(pp)) {
//				pp.message(MessageType.INFO, arc.toString());
//				for (PluginSpec spec : arc.getChildren(pp)) {
//					pp.message(MessageType.INFO, "  " + spec);
//					for (PluginSpec dep : spec.getChildren(pp)) {
//						pp.message(MessageType.INFO, "    " + dep);
//					}
//				}
//			}
//			mgr.start(pp);

//			for(PluginArchive arc : mgr.list(pp, new PluginComponentVisitor() {})) {
//				System.out.println(arc);
//			}
//			mgr.install(pp, new PluginComponentId("com.sshtools@forker-plugin-feature-example-1@0.0.1-SNAPSHOT"));

//			mgr.install(pp, new PluginComponentId("com.sshtools@forker-plugin-example-3"));
//			mgr.start(new PluginComponentId("mypackage3.Test3"), pp);

		}
	}
}
