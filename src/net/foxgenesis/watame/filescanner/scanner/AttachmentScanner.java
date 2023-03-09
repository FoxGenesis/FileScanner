package net.foxgenesis.watame.filescanner.scanner;

import java.util.concurrent.CompletableFuture;

/**
 * 
 * @author Ashley
 *
 */
public interface AttachmentScanner {
	public CompletableFuture<Void> testAttachment(byte[] in, AttachmentData attachment)
			throws AttachmentException;
	
	public boolean shouldTest(AttachmentData attachment);
}
