/**
 * @author 
 *
 * @provides net.foxgenesis.watame.plugin.IPlugin
 */
module watamebot.filescanner {
	requires watamebot;
	requires net.dv8tion.jda;
	requires java.base;
	requires org.slf4j;

	provides net.foxgenesis.watame.plugin.Plugin with net.foxgenesis.watame.filescanner.FileScannerPlugin;
}