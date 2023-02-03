package net.foxgenesis.watame.filescanner;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
import net.foxgenesis.watame.filescanner.scanner.AttachmentScanner.FileSystemException;
import net.foxgenesis.watame.filescanner.scanner.AttachmentScanner.OperatingSystemException;
import net.foxgenesis.watame.filescanner.scanner.LoudVideoDetection;
import net.foxgenesis.watame.filescanner.scanner.MalwareDetection;
import net.foxgenesis.watame.plugin.IPlugin;
import net.foxgenesis.watame.plugin.PluginProperties;

/**
 * @author
 *
 */
@PluginProperties(name = "FileScanner", description = "", version = "1.0.0")
public class FileScannerPlugin implements IPlugin {
	public static final int LOUD_VIDEO = 1, MALWARE_DETECTED = 2;
	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger("FileScanner");
	
	/**
	 * external binaries for offloading processing
	 */
	private File /*ffmpegBinary, ffprobeBinary,*/ qtBinary;

	/**
	 * List of attachment scanners
	 */
	private AttachmentManager scanner;// = new AttachmentManager();

	@Override
	public void preInit() {
		
		//new File("exe").mkdir();
		//make the executable directory
		
		//extract the FFMPEG components to the executable resources location
		String os_name = System.getProperty("os.name").toLowerCase();
		String fs = System.getProperty("file.separator");
		//what a retarded feature of java
		
		if(os_name.startsWith("linux")) {
			qtBinary = new File("lib"+fs+"qt-faststart-i386");		
		}else if(os_name.startsWith("windows")) {
			qtBinary = new File("lib"+fs+"qt-faststart-x86.exe");
		}else {
			throw new OperatingSystemException(System.getProperty("os.name"));
		}
		
		ProcessBuilder ffmpegProcess = new ProcessBuilder("ffmpeg", "-version");
		ProcessBuilder ffprobeProcess = new ProcessBuilder("ffprobe", "-version");
		ProcessBuilder qtProcess = new ProcessBuilder(qtBinary.getPath(), "-v");
        try {
			Process p = ffmpegProcess.start();
			String versionFull = new String(p.getInputStream().readAllBytes());
			String version = versionFull.substring(0, versionFull.indexOf('\n'));
			logger.info("FFMPEG version: " + version);
			p.getInputStream().close();
			p = ffprobeProcess.start();
			versionFull = new String(p.getInputStream().readAllBytes());
			version = versionFull.substring(0, versionFull.indexOf('\n'));
			logger.info("FFprobe version: " + version);
			p.getInputStream().close();
			p = qtProcess.start();
			versionFull = new String(p.getInputStream().readAllBytes());
			version = versionFull.substring(0, versionFull.indexOf('\n'));
			logger.info("Qt-faststart version: " + version);
			p.getInputStream().close();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			logger.error("Failed to get version: ", e);
			
		}
		
        scanner = new AttachmentManager(qtBinary);
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
						}).thenComposeAsync(in -> scanner.testAttachment(in, msg, attachment)) // scan attachment
								.whenComplete((result,err) -> {
									if(err != null) {
										// Call our 'on complete' method
										logger.error("error: ", err);
										if (err instanceof AttachmentException)
											onAttachmentTested(msg, attachment, (AttachmentException) err);
										else
											logger.error("Error while scanning attachment " + attachment, err);
										//return null;
									}
								});
					}
				}//is from guild
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
				logger.info("Removing attachment {}", attachment);
				message.getChannel().sendMessage(message.getAuthor().getAsMention() + " Please do not post loud videos without first stating that the video is \"\n"
						+ "loud in the message. If you are going to post a loud video, describe in the same message that it is loud.").queue();
				break;
			}
			case MALWARE_DETECTED -> {
				logger.info("Removing malicious attachment {}", attachment);
				message.getChannel().sendMessage(message.getAuthor().getAsMention() + " Please do not upload files designed to trigger Antimalware programs. Doing so is against the "
						+ "rules and will get you banned.").queue();
			}
		
		
		}
		message.delete().queue();
		
	}

	@Override
	public void postInit(WatameBot bot) {}

	@Override
	public void onReady(WatameBot bot) {}

	@Override
	public void close() throws Exception {}
	
	
}
