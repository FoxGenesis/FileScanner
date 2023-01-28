package net.foxgenesis.watame.filescanner.scanner;

import java.util.concurrent.CompletableFuture;

import net.dv8tion.jda.api.entities.Message.Attachment;

@FunctionalInterface
public interface AttachmentScanner {
	public CompletableFuture<Void> testAttachment(byte[] in, Attachment attachment) throws AttachmentException;

	public static class AttachmentException extends RuntimeException {
		private static final long serialVersionUID = 5616757818200082472L;

		/**
		 * The exception id
		 */
		public final int id;

		public AttachmentException(int id) { this.id = id; }
	}
}
