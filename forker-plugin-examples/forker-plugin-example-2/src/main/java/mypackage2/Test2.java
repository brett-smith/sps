package mypackage2;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.sshtools.forker.plugin.api.Plugin;
import com.sshtools.forker.plugin.api.PluginEvent;
import com.sshtools.forker.plugin.api.PluginLifecycle;

@Plugin
public class Test2 {
	public final static Log LOG = LogFactory.getLog(Test2.class);

	@PluginLifecycle(event = PluginEvent.START)
	public void start() {
		LOG.info("Start Test 2");
	}

	@PluginLifecycle(event = PluginEvent.CLOSE)
	public void close() {
		LOG.info("Stop Test 2");
	}
}
