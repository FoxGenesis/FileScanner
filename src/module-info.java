/**
 * @author Ashley, Spaz
 *
 * @provides net.foxgenesis.watame.plugin.IPlugin
 */
module watamebot.filescanner {
	requires transitive watamebot;
	requires transitive net.dv8tion.jda;

	exports net.foxgenesis.watame.filescanner;

	provides net.foxgenesis.watame.plugin.Plugin with net.foxgenesis.watame.filescanner.FileScannerPlugin;
}