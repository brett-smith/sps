package mypackage3;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.sshtools.forker.plugin.api.Plugin;
import com.sshtools.forker.plugin.api.PluginEvent;
import com.sshtools.forker.plugin.api.PluginLifecycle;

@Plugin(start = Plugin.StartMode.AUTO)
public class Test3 {
	
	public final static Log LOG = LogFactory.getLog(Test3.class);

	@PluginLifecycle(event = PluginEvent.INSTALL)
	public void install() {
		LOG.info("Install Test 3.");
	}

	@PluginLifecycle(event = PluginEvent.START)
	public void start() {
		LOG.info("Start Test 3");
	}

	@PluginLifecycle(event = PluginEvent.CLOSE)
	public void close() {
		LOG.info("Stop Test 3");
	}
}
