/**
 * @author 
 *
 * @provides net.foxgenesis.watame.plugin.IPlugin
 */
module pluginTemplate {
	requires watamebot;
	requires net.dv8tion.jda;
	requires java.base;
	requires jsr305;

	provides net.foxgenesis.watame.plugin.IPlugin with net.foxgenesis.watame.filescanner.FileScannerPlugin;
}