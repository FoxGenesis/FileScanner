package net.foxgenesis.watame.filescanner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.SubmissionPublisher;
import java.util.regex.Pattern;

import net.foxgenesis.executor.PrefixedThreadFactory;
import net.foxgenesis.property.PropertyMapping;
import net.foxgenesis.property.PropertyType;
import net.foxgenesis.util.resource.ConfigType;
import net.foxgenesis.watame.filescanner.tester.EBUR128Subscriber;
import net.foxgenesis.watame.plugin.IEventStore;
import net.foxgenesis.watame.plugin.Plugin;
import net.foxgenesis.watame.plugin.SeverePluginException;
import net.foxgenesis.watame.plugin.require.PluginConfiguration;
import net.foxgenesis.watame.plugin.require.RequiresIntents;
import net.foxgenesis.watame.property.PluginProperty;

import org.apache.commons.configuration2.Configuration;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;

/**
 * @author Ashley, Spaz-Master
 *
 */
@PluginConfiguration(defaultFile = "/META-INF/configs/settings.properties", identifier = "settings", outputFile = "settings.properties", type = ConfigType.PROPERTIES)
public class FileScannerPlugin extends Plugin implements RequiresIntents {

	/**
	 * Pattern to check if the message is marked as "loud"
	 */
	private static final Pattern LOUD_MESSAGE_PATTERN = Pattern.compile("\\b(loud|ear rape)\\b",
			Pattern.CASE_INSENSITIVE);

	// ===============================================================================================================================

	/**
	 * Thread pool for scanning attachments
	 */
	private final ExecutorService executor;

	/**
	 * Publisher for attachment scanning
	 */
	private final SubmissionPublisher<Message> publisher;

	/**
	 * EBUR128 video scanning
	 */
	private EBUR128Subscriber subscriber;

	/**
	 * Enabled property
	 */
	private PluginProperty enabled;

	public FileScannerPlugin() {
		super();

		boolean commonPool = true;
		int workers = 2;

		for (String id : configurationKeySet()) {
			Configuration config = getConfiguration(id);
			switch (id) {
				case "settings" -> {
					commonPool = config.getBoolean("commonPool", true);
					workers = config.getInt("workerThreads", 2);
				}
			}
		}

		executor = commonPool ? ForkJoinPool.commonPool()
				: Executors.newFixedThreadPool(workers, new PrefixedThreadFactory("Video Scanning"));
		publisher = new SubmissionPublisher<>(executor, Flow.defaultBufferSize());
	}

	@Override
	protected void preInit() {
		// Check if FFMPEG is installed
		if (!isFFMPEGInstalled())
			throw new SeverePluginException("Failed to find FFMPEG!");
		if (!isFFProbeInstalled())
			throw new SeverePluginException("Failed to find FFProbe!");

		try {
			// Find the QuickTime-FastStart binary
			Path qtBinary = Paths.get("lib", getQTLibraryBySystem(System.getProperty("os.name").toLowerCase()));
			logger.trace("QuickTime-FastStart path: " + qtBinary);

			// Set quick time binary if valid
			if (!isQTLibraryValid(qtBinary))
				throw new SeverePluginException("QuickTimeFastStart binary is not valid!");

			subscriber = new EBUR128Subscriber(qtBinary);
			publisher.subscribe(subscriber);
		} catch (UnsupportedOperationException | IOException e) {
			throw new SeverePluginException("Error while getting FastQuickTime library", e);
		}
	}

	@Override
	protected void init(IEventStore builder) {
		enabled = upsertProperty("enabed", true, PropertyType.NUMBER);

		// Create listener that scans all attachments
		builder.registerListeners(this, new ListenerAdapter() {
			@Override
			public void onMessageReceived(MessageReceivedEvent e) {
				if (!e.isFromGuild())
					return;
				// Do not scan non user messages
				if (e.getAuthor().isBot() || e.getAuthor().isSystem())
					return;
				// Check if we can talk
				if (!e.getChannel().canTalk())
					return;

				Message message = e.getMessage();
				// Check if the message is declared as loud
				if (LOUD_MESSAGE_PATTERN.asPredicate().test(message.getContentRaw().replaceAll("\\|\\|.*?\\|\\|", "")))
					return;

				// Check if file scanning is enabled
				if (!enabled.get(e.getGuild(), () -> true, PropertyMapping::getAsBoolean))
					return;

				if (!publisher.isClosed())
					publisher.submit(e.getMessage());
			}
		});
	}

	@Override
	protected void postInit() {}

	@Override
	protected void onReady() {}

	@Override
	protected void close() throws Exception {
		logger.info("Stopping scanning pool...");
		if (publisher != null)
			publisher.close();
		if (subscriber != null)
			subscriber.close();
		if (executor != null && executor != ForkJoinPool.commonPool())
			executor.shutdown();
	}

	private boolean isFFMPEGInstalled() {
		Process p = null;
		try {
			p = new ProcessBuilder("ffmpeg", "-version").start();
			try (InputStream in = p.getInputStream()) {
				logger.info("FFMPEG version: {}", new String(in.readAllBytes()).split("\n", 2)[0].trim());
			}
			return true;
		} catch (Exception e) {
			logger.error("Error while validating FFMPEG version", e);
			return false;
		} finally {
			if (p != null)
				p.destroy();
		}
	}

	private boolean isFFProbeInstalled() {
		Process p = null;
		try {
			p = new ProcessBuilder("ffprobe", "-version").start();
			try (InputStream in = p.getInputStream()) {
				logger.info("FFProbe version: " + new String(in.readAllBytes()).split("\n", 2)[0].trim());
			}
			return true;
		} catch (Exception e) {
			logger.error("Error while validating FFProbe version", e);
			return false;
		} finally {
			if (p != null)
				p.destroy();
		}
	}

	private boolean isQTLibraryValid(Path path) throws IOException {
		Process p = null;
		try {
			p = new ProcessBuilder(path.toString(), "-v").start();
			try (InputStream in = p.getInputStream()) {
				String version = new String(in.readAllBytes()).split("\n", 2)[0].trim();

				if (version == null || version.isBlank())
					throw new IOException("Unable to get version");

				logger.info("QuickTime-FastStart library version: {}", version);
			}
			return true;
		} finally {
			if (p != null)
				p.destroy();
		}
	}

	private static String getQTLibraryBySystem(String system) {
		if (system.startsWith("linux"))
			return "qtfs";
		if (system.startsWith("windows"))
			return "qt-faststart-x86.exe";

		throw new UnsupportedOperationException("[QuickTime-FastStart] Unsupported Operating System: " + system);
	}

	@Override
	public EnumSet<GatewayIntent> getRequiredIntents() {
		return EnumSet.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT);
	}
}
