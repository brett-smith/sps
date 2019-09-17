package mypackage1;

import com.sshtools.forker.plugin.api.Plugin;
import com.sshtools.forker.plugin.api.PluginEvent;
import com.sshtools.forker.plugin.api.PluginLifecycle;

@Plugin
public class TestLibPlugin {
	@PluginLifecycle(event = PluginEvent.START)
	public void start() {
		System.out.println("Start Lib");
	}

	@PluginLifecycle(event = PluginEvent.CLOSE)
	public void close() {
		System.out.println("Stop Lib");
	}
}
