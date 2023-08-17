package net.foxgenesis.watame.filescanner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.regex.Pattern;

import net.foxgenesis.executor.PrefixedThreadFactory;
import net.foxgenesis.property.PropertyMapping;
import net.foxgenesis.property.PropertyType;
import net.foxgenesis.watame.WatameBot;
import net.foxgenesis.watame.filescanner.tester.EBUR128Subscriber;
import net.foxgenesis.watame.plugin.IEventStore;
import net.foxgenesis.watame.plugin.Plugin;
import net.foxgenesis.watame.plugin.SeverePluginException;
import net.foxgenesis.watame.property.PluginProperty;

import org.apache.commons.configuration2.Configuration;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * @author Ashley, Spaz-Master
 *
 */
public class FileScannerPlugin extends Plugin {
	/**
	 * Thread pool for scanning attachments
	 */
	private static final ExecutorService executor = Executors
			.newCachedThreadPool(new PrefixedThreadFactory("Video Scanning"));

	/**
	 * Pattern to check if the message is marked as "loud"
	 */
	private static final Pattern LOUD_MESSAGE_PATTERN = Pattern.compile("\\b(loud|ear rape)\\b",
			Pattern.CASE_INSENSITIVE);
	/**
	 * Enabled property
	 */
	private PluginProperty enabled;

	// ===============================================================================================================================

	/**
	 * Publisher for attachment scanning
	 */
	private SubmissionPublisher<Message> publisher = new SubmissionPublisher<>(executor, Flow.defaultBufferSize());

	/**
	 * EBUR128 video scanning
	 */
	private EBUR128Subscriber subscriber;

	@Override
	protected void onConstruct(Properties meta, Map<String, Configuration> configs) {}

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
				// Check if message was found a guild and scanning is enabled
				if (e.isFromGuild() && !(e.getAuthor().isBot() || e.getAuthor().isSystem())
						&& enabled.get(e.getGuild(), () -> true, PropertyMapping::getAsBoolean)) {
					Message message = e.getMessage();

					// Check to make sure message isn't declared as loud
					if (!LOUD_MESSAGE_PATTERN.asPredicate()
							.test(message.getContentRaw().replaceAll("\\|\\|.*?\\|\\|", "")))
						if (!publisher.isClosed())
							publisher.submit(e.getMessage());
				}
			}
		});
	}

	@Override
	protected void postInit(WatameBot bot) {}

	@Override
	protected void onReady(WatameBot bot) {}

	@Override
	protected void close() throws Exception {
		logger.info("Stopping scanning pool...");
		executor.shutdown();
		if (publisher != null)
			publisher.close();
		if (subscriber != null)
			subscriber.close();
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
				logger.info("QuickTime-FastStart library version: {}",
						new String(in.readAllBytes()).split("\n", 2)[0].trim());
			}
			return true;
		} finally {
			if (p != null)
				p.destroy();
		}
	}

	private static String getQTLibraryBySystem(String system) {
		if (system.startsWith("linux"))
			return "qt-faststart-i386";
		if (system.startsWith("windows"))
			return "qt-faststart-x86.exe";

		throw new UnsupportedOperationException("[QuickTime-FastStart] Unsupported Operating System: " + system);
	}
}
