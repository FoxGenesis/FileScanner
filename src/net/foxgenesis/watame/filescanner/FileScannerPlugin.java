package net.foxgenesis.watame.filescanner;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.internal.utils.IOUtil;
import net.foxgenesis.watame.WatameBot;
import net.foxgenesis.watame.WatameBot.ProtectedJDABuilder;
import net.foxgenesis.watame.filescanner.scanner.AttachmentManager;
import net.foxgenesis.watame.filescanner.scanner.AttachmentScanner.AttachmentException;
import net.foxgenesis.watame.filescanner.scanner.AttachmentScanner.OperatingSystemException;
import net.foxgenesis.watame.filescanner.scanner.LoudVideoDetection;
import net.foxgenesis.watame.filescanner.scanner.MalwareDetection;
import net.foxgenesis.watame.plugin.IPlugin;
import net.foxgenesis.watame.plugin.PluginProperties;

/**
 * @author Ashley, Spaz-Master-
 *
 */
@PluginProperties(name = "FileScanner", description = "", version = "1.0.0")
public class FileScannerPlugin implements IPlugin {
	public static final int LOUD_VIDEO = 1, MALWARE_DETECTED = 2;
	public static final Executor SCANNING_POOL = Executors
			.newFixedThreadPool(ForkJoinPool.getCommonPoolParallelism() + 2);
	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger("FileScanner");

	/**
	 * external binaries for off-loading processing
	 */
	private Path qtBinary;

	/**
	 * List of attachment scanners
	 */
	private AttachmentManager scanner;

	@Override
	public void preInit() {
		// Find the QuickTime-FastStart binary
		try {
			qtBinary = Paths.get("lib", getQTLibraryBySystem(System.getProperty("os.name").toLowerCase()));
		} catch (OperatingSystemException e) {
			logger.error("Error while getting FastQuickTime library", e);
			return;
		}

		// Validate our required binaries
		if(!isFFMPEGInstalled()) {
			logger.error("Failed to find FFMPEG!");
			return;
		}
		else if (!isFFProbeInstalled()) {
			logger.error("Failed to find FFProbe!");
			return;
		} else if(!isQTLibraryValid(qtBinary)) {
			logger.error("Failed to find QuickTime-FastStart binary");
			return;
		}

		try {
			scanner = new AttachmentManager(qtBinary);
		} catch (FileNotFoundException e) {
			logger.error("Error while creating attachment manager", e);
			return;
		}
		// Create our attachment scanners
		scanner.addScanner(new LoudVideoDetection());
		scanner.addScanner(new MalwareDetection());
	}

	@Override
	public void init(ProtectedJDABuilder builder) {
		// Create listener that scans all attachments
		builder.addEventListeners(new ListenerAdapter() {
			@Override
			public void onMessageReceived(MessageReceivedEvent e) {
				if (e.isFromGuild()) {
					Message msg = e.getMessage();

					// Iterate on the attachments
					for (Attachment attachment : msg.getAttachments()) {

						logger.trace("Attempting to scan {} ", attachment);
						attachment.getProxy().download().thenApplyAsync(stream -> {
							// Read all the bytes in the stream and then close it
							try {
								return stream.readAllBytes();
							} catch (IOException e1) {
								throw new CompletionException(e1);
							} finally {
								IOUtil.silentClose(stream);
							}
						}).thenComposeAsync(in -> scanner.testAttachment(in, msg, attachment)) // scan attachment
								.exceptionallyAsync(err -> {
									if (err instanceof AttachmentException)
										onAttachmentTested(msg, attachment, ((AttachmentException) err).id);
									else // Error thrown while scanning
										logger.error("Error while scanning attachment " + attachment, err);
									return null;
								});

					}
				}
			}
		});
	}

	/**
	 * Called when attachment scanning has returned a result
	 * 
	 * @param message    - the message the attachment belongs to
	 * @param attachment - the attachment that was scanned
	 * @param status     - scan result
	 */
	private void onAttachmentTested(Message message, Attachment attachment, int result) {
		switch (result) {
		case LOUD_VIDEO -> {
			logger.info("Removing attachment {}", attachment);
			message.reply(message.getAuthor().getAsMention()
					+ " Please do not post loud videos without first stating that the video is \"\n"
					+ "loud in the message. If you are going to post a loud video, describe in the same message that it is loud.")
					.flatMap(m -> message.delete().reason("Loud video")).queue();
		}
		case MALWARE_DETECTED -> {
			logger.info("Removing malicious attachment {}", attachment);
			message.reply(message.getAuthor().getAsMention()
					+ " Please do not upload files designed to trigger Antimalware programs. Doing so is against the "
					+ "rules and will get you banned.").flatMap(m -> message.delete().reason("Malicious attachment"))
					.queue();
		}
		}
	}

	@Override
	public void postInit(WatameBot bot) {}

	@Override
	public void onReady(WatameBot bot) {}

	@Override
	public void close() throws Exception {}

	private static boolean isFFMPEGInstalled() {
		Process p = null;
		try {
			p = new ProcessBuilder("ffmpeg", "-version").start();
			logger.info("FFMPEG version: " + new String(p.getInputStream().readAllBytes()).split("\n", 2)[0]);
			return true;
		} catch (Exception e) {
			logger.error("Error while validating FFMPEG version", e);
			return false;
		} finally {
			if (p != null)
				p.destroy();
		}
	}

	private static boolean isFFProbeInstalled() {
		Process p = null;
		try {
			p = new ProcessBuilder("ffprobe", "-version").start();
			logger.info("FFProbe version: " + new String(p.getInputStream().readAllBytes()).split("\n", 2)[0]);
			return true;
		} catch (Exception e) {
			logger.error("Error while validating FFProbe version", e);
			return false;
		} finally {
			if (p != null)
				p.destroy();
		}
	}

	private static boolean isQTLibraryValid(Path path) {
		Process p = null;
		try {
			p = new ProcessBuilder(path.toString(), "-v").start();
			logger.info("QuickTime-FastStart library version: "
					+ new String(p.getInputStream().readAllBytes()).split("\n", 2)[0]);
			return true;
		} catch (Exception e) {
			logger.error("Error while validating QuickTime-FastStart library", e);
			return false;
		} finally {
			if (p != null)
				p.destroy();
		}
	}

	private static String getQTLibraryBySystem(String system) throws OperatingSystemException {
		if (system.startsWith("linux"))
			return "qt-faststart-i386";
		else if (system.startsWith("windows"))
			return "qt-faststart-x86.exe";

		throw new OperatingSystemException(System.getProperty("os.name"));
	}
}
