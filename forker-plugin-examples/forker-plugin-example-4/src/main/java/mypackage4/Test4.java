package mypackage4;

import com.sshtools.forker.plugin.api.Manager;
import com.sshtools.forker.plugin.api.Plugin;
import com.sshtools.forker.plugin.api.PluginEntryPoint;
import com.sshtools.forker.plugin.api.PluginManager;

@Plugin(start = Plugin.StartMode.AUTO, staticLoad = true)
public class Test4 {
	@Manager
	private static PluginManager manager;

	@PluginEntryPoint
	public static void main(String[] args) throws Exception {
		System.out.println("Test4XX - Entry point. Will complete in 30 seconds");
		Thread.sleep(30000);
		System.out.println("Plugin manager reported state: " + manager.getState());
	}
}
