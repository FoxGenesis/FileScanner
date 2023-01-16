package my.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.foxgenesis.watame.WatameBot;
import net.foxgenesis.watame.WatameBot.ProtectedJDABuilder;
import net.foxgenesis.watame.plugin.IPlugin;
import net.foxgenesis.watame.plugin.PluginProperties;

/**
 * @author
 *
 */
@PluginProperties(name = "", description = "", version = "0.0.0", providesCommands = false)
public class PluginTemplate implements IPlugin {
	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger("PluginTemplate");
	
	@Override
	public void preInit() { 
		// TODO Auto-generated method stub
	 }

	@Override
	public void init(ProtectedJDABuilder builder) { 
		// TODO Auto-generated method stub
	 }

	@Override
	public void postInit(WatameBot bot) { 
		// TODO Auto-generated method stub
	 }

	@Override
	public void onReady(WatameBot bot) { 
		// TODO Auto-generated method stub
	 }
	
	@Override
	public void close() throws Exception { 
		// TODO Auto-generated method stub
	 }
}
