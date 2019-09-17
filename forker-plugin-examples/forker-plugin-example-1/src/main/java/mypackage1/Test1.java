package mypackage1;

import com.sshtools.forker.plugin.api.Plugin;
import com.sshtools.forker.plugin.api.PluginEvent;
import com.sshtools.forker.plugin.api.PluginLifecycle;
import com.sshtools.icongenerator.IconBuilder;

@Plugin(dependencies = { "TestLibPlugin", "com.sshtools@forker-plugin-example-2@~@mypackage2.Test2" }, name = "A Library")
//@Plugin(dependencies = { "TestLibPlugin" }, name = "A Library")
//@Plugin
public class Test1 {

	@PluginLifecycle(event = PluginEvent.START)
	public void start() {
		System.out.println("Start Test");

		IconBuilder ib = new IconBuilder();
		ib.autoTextColor();
		ib.text("Something");
	}

	@PluginLifecycle(event = PluginEvent.CLOSE)
	public void close() {
		System.out.println("Stop Test");
	}
}
