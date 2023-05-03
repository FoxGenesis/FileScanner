package net.foxgenesis.watame.filescanner;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.configuration2.Configuration;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.RestAction;
import net.foxgenesis.property.IProperty;
import net.foxgenesis.util.StringUtils;
import net.foxgenesis.watame.WatameBot;
import net.foxgenesis.watame.filescanner.scanner.AttachmentData;
import net.foxgenesis.watame.filescanner.scanner.AttachmentException;
import net.foxgenesis.watame.filescanner.scanner.AttachmentManager;
import net.foxgenesis.watame.filescanner.scanner.QuickTimeAttachmentManager;
import net.foxgenesis.watame.filescanner.scanner.scanners.LoudVideoDetectionTest;
import net.foxgenesis.watame.filescanner.scanner.scanners.MalwareDetection;
import net.foxgenesis.watame.plugin.IEventStore;
import net.foxgenesis.watame.plugin.Plugin;
import net.foxgenesis.watame.plugin.PluginConfiguration;
import net.foxgenesis.watame.plugin.SeverePluginException;
import net.foxgenesis.watame.property.IGuildPropertyMapping;

/**
 * @author Ashley, Spaz-Master
 *
 */
@PluginConfiguration(defaultFile = "/META-INF/settings.ini", identifier = "settings", outputFile = "settings.ini")
public class FileScannerPlugin extends Plugin {
	/**
	 * Thread pool for scanning attachments
	 */
	public static final ExecutorService SCANNING_POOL = Executors.newWorkStealingPool();

	/**
	 * Enabled property
	 */
	private static final IProperty<String, Guild, IGuildPropertyMapping> enabled = WatameBot.INSTANCE
			.getPropertyProvider().getProperty("filescanner_enabled");

	// ===============================================================================================================================

	/**
	 * List of attachment scanners
	 */
	private AttachmentManager scanner;

	/**
	 * Is QuickTime-FastStart enabled
	 */
	private boolean useQT;

	@Override
	protected void onPropertiesLoaded(Properties properties) {}

	@Override
	protected void onConfigurationLoaded(String identifier, Configuration properties) {
		switch (identifier) {
			case "settings" -> { this.useQT = properties.getBoolean("qt.enabled", true); }
		}
	}

	@Override
	protected void preInit() {
		// Check if FFMPEG is installed
		if (!isFFMPEGInstalled())
			throw new SeverePluginException("Failed to find FFMPEG!");
		else if (!isFFProbeInstalled())
			throw new SeverePluginException("Failed to find FFProbe!");

		if (this.useQT)
			try {
				// Find the QuickTime-FastStart binary
				Path qtBinary = Paths.get("lib", getQTLibraryBySystem(System.getProperty("os.name").toLowerCase()));
				this.logger.trace("QuickTime-FastStart path: " + qtBinary);

				// Use the QuickTime-FastStart attachment manager if the binary is valid
				this.scanner = new QuickTimeAttachmentManager(qtBinary, SCANNING_POOL);

			} catch (UnsupportedOperationException | IOException e) {
				this.logger.error("Error while getting FastQuickTime library", e);

				// Fallback to default attachment manager
				this.logger.warn("Using fallback attachment manager (NO QUICKTIME)!");
				this.scanner = new AttachmentManager();
			}
		else
			this.scanner = new AttachmentManager(SCANNING_POOL);

		// Create our attachment scanners
		this.scanner.addScanner(new LoudVideoDetectionTest());
		this.scanner.addScanner(new MalwareDetection());
		// scanner.addScanner(new ResolutionScanner());
	}

	@Override
	protected void init(IEventStore builder) {
		// Create listener that scans all attachments
		builder.registerListeners(this, new ListenerAdapter() {
			@Override
			public void onMessageReceived(MessageReceivedEvent e) {
				// Check if message was found a guild and scanning is enabled
				if (e.isFromGuild() && enabled.get(e.getGuild(), true, IGuildPropertyMapping::getAsBoolean)) {
					Message msg = e.getMessage();

					// Collect all attachments
					List<AttachmentData> attachments = new ArrayList<>();
					msg.getAttachments().forEach(a -> attachments.add(new AttachmentData(msg, a)));
					StringUtils.findURLs(msg.getContentRaw()).forEach(u -> attachments.add(new AttachmentData(msg, u)));

					// Iterate on the attachments
					attachments.stream().filter(scanner::canScan).forEach(attachment -> {
						String filename = attachment.getFileName();
						// Download attachment
						FileScannerPlugin.this.logger.debug("Scanning {} ", filename);
						attachment.getData(SCANNING_POOL)
								.thenComposeAsync(in -> scanner.testAttachment(in, attachment), SCANNING_POOL)

								// Error thrown in attachment scanner
								.exceptionallyAsync(err -> {
									if (err instanceof CompletionException) {
										Throwable cause = err.getCause();

										// Check if this was the result of a flag from the scanner
										if (cause instanceof AttachmentException) {
											onAttachmentTested(msg, attachment,
													((AttachmentException) err.getCause()).result);
											return null;
										}

										// Attachment was not found
										if (cause instanceof FileNotFoundException) {
											logger.warn("Failed to find attachment [{}]. Skipping...", filename);
											return null;
										}

										// HTTP request errors
										if (cause instanceof IOException && err.getMessage()
												.startsWith("Server returned HTTP response code: ")) {
											logger.warn("Failed to open attachment [{}]. Skipping...", filename);
											return null;
										}
									}

									// Error thrown while scanning
									logger.error("Error while scanning attachment " + filename, err);
									return null;
								}, SCANNING_POOL);
					});
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

		int size = SCANNING_POOL.shutdownNow().size();
		if (size > 0)
			logger.info("Force stopping {} scanning pool task(s)...", size);

		if (!SCANNING_POOL.awaitTermination(5, TimeUnit.SECONDS))
			logger.warn("Timed out waiting for scanning pool shutdown! Continuing...");

	}

	// ===============================================================================================================================

	public static enum ScanResult {
		NONE(0), LOUD_VIDEO(1), MALWARE_HINT(2), MALWARE_TEST(3), CRASHER_VIDEO(4);

		public final int id;

		ScanResult(int id) {
			this.id = id;
		}

		public static ScanResult fromID(int id) {
			for (ScanResult result : ScanResult.values())
				if (result.id == id)
					return result;
			return null;
		}
	}

	/**
	 * Called when attachment scanning has returned a result
	 * 
	 * @param message    - the message the attachment belongs to
	 * @param attachment - the attachment that was scanned
	 * @param status     - scan result
	 */
	private void onAttachmentTested(Message message, AttachmentData attachment, ScanResult result) {
		RestAction<?> action = switch (result) {
			case LOUD_VIDEO -> {
				this.logger.info("Removing attachment {}", attachment.getFileName());
				yield message.replyEmbeds(getLoudVideoEmbed(message))
						.flatMap(m -> canDoInChannel(message.getGuildChannel(), Permission.MESSAGE_MANAGE),
								m -> message.delete().reason("Loud video"))
						.onErrorMap(err -> {
							logger.error("Failed to remove attachment " + attachment.getFileName(), err);
							return null;
						});
			}

			case MALWARE_HINT, MALWARE_TEST -> {
				MalwareType type = MalwareType.fromID(result.id);
				this.logger.info("Removing malicious attachment [{}] : {}", type, attachment.getFileName());

				yield message.replyEmbeds(getMalwareEmbed(message, type))
						.flatMap(m -> canDoInChannel(message.getGuildChannel(), Permission.MESSAGE_MANAGE),
								m -> message.delete().reason("Malicious attachment"))
						.onErrorMap(err -> {
							logger.error("Failed to remove attachment " + attachment.getFileName(), err);
							return null;
						});
			}
			default -> throw new IllegalArgumentException("Unexpected value: " + result);
		};

		if (action != null)
			action.queue();
	}

	// ===============================================================================================================================

	private static MessageEmbed getLoudVideoEmbed(Message message) {
		return new EmbedBuilder().setColor(0xF44336).setTitle("Loud Video Detected").setDescription(message.getAuthor()
				.getAsMention() + " Please do not post loud videos without first stating that the video is "
				+ "loud in the message. If you are going to post a loud video, describe in the same message that it is loud.")
				.setThumbnail("https://www.kindpng.com/picc/m/275-2754352_sony-mdrv6-anime-hd-png-download.png")
				.build();
	}

	private static MessageEmbed getMalwareEmbed(Message message, MalwareType type) {
		return new EmbedBuilder().setColor(0xF44336).setTitle("Malware Detected").setDescription(message.getAuthor()
				.getAsMention()
				+ " Please do not upload files designed to trigger Antimalware programs. Doing so is against the "
				+ "rules and will get you banned.").addField("Malware Type", type.friendlyName, true)
				.setThumbnail("https://media.tenor.com/JwnY0jHr7_MAAAAi/bonk-cat-ouch.gif").build();
	}

	private static boolean canDoInChannel(GuildMessageChannelUnion channel, Permission... permissions) {
		return channel.getGuild().getSelfMember().hasPermission(channel, permissions);
	}

	// ===============================================================================================================================

	private boolean isFFMPEGInstalled() {
		Process p = null;
		try {
			p = new ProcessBuilder("ffmpeg", "-version").start();
			try (InputStream in = p.getInputStream()) {
				this.logger.info("FFMPEG version: {}", new String(in.readAllBytes()).split("\n", 2)[0].trim());
			}
			return true;
		} catch (Exception e) {
			this.logger.error("Error while validating FFMPEG version", e);
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
				this.logger.info("FFProbe version: " + new String(in.readAllBytes()).split("\n", 2)[0].trim());
			}
			return true;
		} catch (Exception e) {
			this.logger.error("Error while validating FFProbe version", e);
			return false;
		} finally {
			if (p != null)
				p.destroy();
		}
	}

	private static String getQTLibraryBySystem(String system) {
		if (system.startsWith("linux"))
			return "qt-faststart-i386";
		else if (system.startsWith("windows"))
			return "qt-faststart-x86.exe";

		throw new UnsupportedOperationException("[QuickTime-FastStart] Unsupported Operating System: " + system);
	}
}
