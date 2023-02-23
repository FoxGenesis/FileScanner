package net.foxgenesis.watame.filescanner.scanner;

import java.util.concurrent.CompletableFuture;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;

/**
 * 
 * @author Ashley
 *
 */
public interface AttachmentScanner {
	public CompletableFuture<Void> testAttachment(byte[] in, Message msg, Attachment attachment)
			throws AttachmentException;
	
	public boolean shouldTest(Message message, Attachment attachment);
}
