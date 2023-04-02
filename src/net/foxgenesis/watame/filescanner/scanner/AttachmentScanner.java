package net.foxgenesis.watame.filescanner.scanner;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 
 * @author Ashley
 *
 */
public interface AttachmentScanner {
	public CompletableFuture<Void> testAttachment(byte[] in, AttachmentData attachment, Executor executor)
			throws AttachmentException;
	
	public boolean shouldTest(AttachmentData attachment);
}
