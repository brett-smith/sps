import com.sshtools.forker.plugin.api.Plugin;
import com.sshtools.forker.plugin.api.PluginEvent;
import com.sshtools.forker.plugin.api.PluginLifecycle;

@Plugin
public class AMain {

	@PluginLifecycle(event = PluginEvent.POST_START)
	public void main(String[] args) {
		System.out.println("XXXXX A!");
	}
}
