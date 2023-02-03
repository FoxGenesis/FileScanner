package net.foxgenesis.watame.filescanner.scanner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;

public class AttachmentManager {
	private static final Logger logger = LoggerFactory.getLogger("AttachmentManager");

	private final List<AttachmentScanner> scanners = new ArrayList<>();
	private File qt_faststartBinary;

	public AttachmentManager(File qt) {
		qt_faststartBinary = qt;
	}

	public boolean addScanner(AttachmentScanner scanner) {
		return scanner == null ? false : scanners.contains(scanner) ? false : scanners.add(scanner);
	}

	public CompletableFuture<Void> testAttachment(byte[] in, Message msg, Attachment attachment) {

		if(in == null)
			return CompletableFuture.failedFuture(new NullPointerException("Attachment data was null"));
		
		return CompletableFuture.supplyAsync(() -> {

			if (!attachment.isVideo())
				return in;

			if (!attachment.getFileExtension().equals("webm")) {
				
				return formatToQT(in);
				
			}

			return in;

		}).thenComposeAsync(newData -> CompletableFuture
				.anyOf(scanners.stream().map(scanner -> scanner.testAttachment(newData, msg, attachment)).toList()
						.toArray(new CompletableFuture[scanners.size()])))
				.thenApply(obj -> null);
	}
	
	
	
	/**
	 * Converts inputted quicktime files (MOV, MP4) into faststart format
	 * 
	 * @author Spaz-Master
	 * @param input	- input data of file to convert to quicktime faststart format, 
	 * @return 		- the newly faststart format file
	 */
	private byte[] formatToQT(byte[] input) {
		ProcessBuilder pb = new ProcessBuilder(qt_faststartBinary.getPath(), "-q");
		Process p = null;
		boolean osClose = false;
		try {
			p = pb.start();
			p.getOutputStream().write(input);
			p.getOutputStream().flush();
			p.getOutputStream().close();
			osClose = true;
			input = p.getInputStream().readAllBytes();
			
		} catch (IOException e) {
			logger.error("Failed to start Qt-FastStart binary: ", e);
			return input;
		}finally {
			try {
				if(p != null) {
					if(osClose)
						//I HATE THIS FUCKIN LANGUAGE
						p.getOutputStream().close();
					p.getInputStream().close();
				}
			} catch (IOException e) {}
			
		}
		
		return input;
	}
}
