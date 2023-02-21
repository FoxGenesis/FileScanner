package net.foxgenesis.watame.filescanner;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.commons.configuration2.PropertiesConfiguration;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.internal.utils.IOUtil;
import net.foxgenesis.property.IPropertyField;
import net.foxgenesis.watame.ProtectedJDABuilder;
import net.foxgenesis.watame.WatameBot;
import net.foxgenesis.watame.filescanner.executor.FileScannerThreadFactory;
import net.foxgenesis.watame.filescanner.scanner.AttachmentException;
import net.foxgenesis.watame.filescanner.scanner.AttachmentManager;
import net.foxgenesis.watame.filescanner.scanner.LoudVideoDetection;
import net.foxgenesis.watame.filescanner.scanner.MalwareDetection;
import net.foxgenesis.watame.filescanner.scanner.MalwareType;
import net.foxgenesis.watame.filescanner.scanner.QuickTimeAttachmentManager;
import net.foxgenesis.watame.plugin.Plugin;
import net.foxgenesis.watame.plugin.PluginConfiguration;
import net.foxgenesis.watame.plugin.SeverePluginException;
import net.foxgenesis.watame.property.IGuildPropertyMapping;
import net.foxgenesis.watame.util.DiscordUtils;

/**
 * @author Ashley, Spaz-Master
 *
 */
@PluginConfiguration(defaultFile = "/META-INF/settings.ini", identifier = "settings", outputFile = "settings.ini")
public class FileScannerPlugin extends Plugin {
	/**
	 * Thread pool for scanning attachments
	 */
	public static final Executor SCANNING_POOL = Executors
			.newCachedThreadPool(new FileScannerThreadFactory("File Scanning"));

	/**
	 * Enabled property
	 */
	private static final IPropertyField<String, Guild, IGuildPropertyMapping> enabled = WatameBot.getInstance()
			.getPropertyProvider().getProperty("filescanner-enabled");

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
	protected void onConfigurationLoaded(String identifier, PropertiesConfiguration properties) {
		switch (identifier) {
		case "settings" -> { useQT = properties.getBoolean("qt.enabled", true); }
		}
	}

	@Override
	protected void preInit() {
		// Check if FFMPEG is installed
		if (!isFFMPEGInstalled())
			throw new SeverePluginException("Failed to find FFMPEG!");
		else if (!isFFProbeInstalled())
			throw new SeverePluginException("Failed to find FFProbe!");

		if (useQT)
			try {
				// Find the QuickTime-FastStart binary
				Path qtBinary = Paths.get("lib", getQTLibraryBySystem(System.getProperty("os.name").toLowerCase()));
				logger.trace("QuickTime-FastStart path: " + qtBinary);

				// Use the QuickTime-FastStart attachment manager if the binary is valid
				scanner = new QuickTimeAttachmentManager(qtBinary);

			} catch (UnsupportedOperationException | IOException e) {
				logger.error("Error while getting FastQuickTime library", e);

				// Fallback to default attachment manager
				logger.warn("Using fallback attachment manager (NO QUICKTIME)!");
				scanner = new AttachmentManager();
			}
		else
			scanner = new AttachmentManager();

		// Create our attachment scanners
		scanner.addScanner(new LoudVideoDetection());
		scanner.addScanner(new MalwareDetection());
		// scanner.addScanner(new ResolutionScanner());
	}

	@Override
	protected void init(ProtectedJDABuilder builder) {
		// Create listener that scans all attachments
		builder.addEventListeners(new ListenerAdapter() {
			@Override
			public void onMessageReceived(MessageReceivedEvent e) {
				if (e.isFromGuild() && enabled.get(e.getGuild(), true, IGuildPropertyMapping::getAsBoolean)) {
					Message msg = e.getMessage();

					// Iterate on the attachments
					msg.getAttachments().stream().forEach(attachment -> {
						logger.debug("Attempting to scan {} ", attachment.getFileName());

						// Stream the data of the attachment
						attachment.getProxy().download().thenApplyAsync(stream -> {
							try {
								// Read all the bytes in the stream and then close it
								return stream.readAllBytes();
							} catch (IOException e1) {
								throw new CompletionException(e1);
							} finally {
								IOUtil.silentClose(stream);
							}

							// Pass attachment scanning to the attachment manager
						}).thenCompose(in -> scanner.testAttachment(in, msg, attachment))

								// Error thrown in attachment scanner
								.exceptionallyAsync(err -> {
									if (err instanceof CompletionException) {
										// Check if this was the result of a flag from the scanner
										if (err.getCause() instanceof AttachmentException) {
											onAttachmentTested(msg, attachment,
													((AttachmentException) err.getCause()).id);
											return null;
										}
									}

									// Error thrown while scanning
									logger.error("Error while scanning attachment " + attachment.getFileName(), err);
									return null;
								});
					});
				}
			}
		});
	}

	public static final int LOUD_VIDEO = 1, MALWARE_HINT = 2, MALWARE_TEST = 3, CRASHER_VIDEO = 4;

	/**
	 * Called when attachment scanning has returned a result
	 * 
	 * @param message    - the message the attachment belongs to
	 * @param attachment - the attachment that was scanned
	 * @param status     - scan result
	 */
	private void onAttachmentTested(Message message, Attachment attachment, int result) {
		RestAction<?> action = switch (result) {
		case LOUD_VIDEO -> {
			logger.info("Removing attachment {}", attachment.getFileName());
			yield message.replyEmbeds(getLoudVideoEmbed(message)).flatMap(
					m -> canDoInChannel(message.getGuildChannel(), Permission.MESSAGE_MANAGE),
					m -> message.delete().reason("Loud video"));
		}

		case MALWARE_HINT, MALWARE_TEST -> {
			MalwareType type = MalwareType.fromID(result);
			logger.info("Removing malicious attachment [{}] : {}", type, attachment.getFileName());

			yield message.replyEmbeds(getMalwareEmbed(message, type)).flatMap(
					m -> canDoInChannel(message.getGuildChannel(), Permission.MESSAGE_MANAGE),
					m -> message.delete().reason("Malicious attachment"));
		}
		default -> throw new IllegalArgumentException("Unexpected value: " + result);
		};

		if (action != null)
			action.queue();
	}

	@Override
	protected void postInit(WatameBot bot) {}

	@Override
	protected void onReady(WatameBot bot) {}

	@Override
	public void close() {}

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
		return DiscordUtils.getBotMember(channel.getGuild()).hasPermission(channel, permissions);
	}

	// ===============================================================================================================================

	private boolean isFFMPEGInstalled() {
		Process p = null;
		try {
			p = new ProcessBuilder("ffmpeg", "-version").start();
			logger.info("FFMPEG version: " + new String(p.getInputStream().readAllBytes()).split("\n", 2)[0].trim());
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
			logger.info("FFProbe version: " + new String(p.getInputStream().readAllBytes()).split("\n", 2)[0].trim());
			return true;
		} catch (Exception e) {
			logger.error("Error while validating FFProbe version", e);
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
