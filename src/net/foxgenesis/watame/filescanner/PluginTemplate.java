package net.foxgenesis.watame.filescanner;

import java.io.IOException;
import java.util.concurrent.CompletionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.foxgenesis.watame.WatameBot;
import net.foxgenesis.watame.WatameBot.ProtectedJDABuilder;
import net.foxgenesis.watame.filescanner.scanner.AttachmentManager;
import net.foxgenesis.watame.filescanner.scanner.AttachmentScanner.AttachmentException;
import net.foxgenesis.watame.filescanner.scanner.LoudVideoDetection;
import net.foxgenesis.watame.filescanner.scanner.MalwareDetection;
import net.foxgenesis.watame.plugin.IPlugin;
import net.foxgenesis.watame.plugin.PluginProperties;

/**
 * @author
 *
 */
@PluginProperties(name = "FileScanner", description = "", version = "1.0.0")
public class PluginTemplate implements IPlugin {
	private static final int LOUD_VIDEO = 1, MALWARE_DETECTED = 2;
	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger("FileScanner");

	/**
	 * List of attachment scanners
	 */
	private AttachmentManager scanner = new AttachmentManager();

	@Override
	public void preInit() {
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
								try {
									stream.close();
								} catch (IOException e1) {}
							}
						}).thenComposeAsync(in -> scanner.testAttachment(in, attachment)) // scan attachment
								.exceptionallyAsync(err -> {
									// Call our 'on complete' method
									if (err instanceof AttachmentException)
										onAttachmentTested(msg, attachment, (AttachmentException) err);
									else
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
	private void onAttachmentTested(Message message, Attachment attachment, AttachmentException result) {
		// Do stuff with results
		switch (result.id) {
		case LOUD_VIDEO -> {

		}
		case MALWARE_DETECTED -> {

		}
		}
	}

	@Override
	public void postInit(WatameBot bot) {}

	@Override
	public void onReady(WatameBot bot) {}

	@Override
	public void close() throws Exception {}
}
