package net.foxgenesis.watame.filescanner.scanner;

import java.io.File;
import java.util.concurrent.CompletableFuture;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;

/**
 * 
 * @author Ashley
 *
 */
@FunctionalInterface
public interface AttachmentScanner {
	public CompletableFuture<Void> testAttachment(byte[] in, Message msg, Attachment attachment)
			throws AttachmentException;

	public static class AttachmentException extends RuntimeException {
		private static final long serialVersionUID = 5616757818200082472L;

		/**
		 * The exception id
		 */
		public final int id;

		public AttachmentException(int id) { this.id = id; }
	}

	/**
	 * @author Spaz-Master
	 *
	 */
	public static class OperatingSystemException extends Exception {

		private static final long serialVersionUID = -1920850180721778217L;
		private final String os_name;

		public OperatingSystemException(String os) { os_name = os; }

		public String getReason() { return "Operating System \"" + os_name + "\" is not supported"; }

	}

	/**
	 * @author Spaz-Master
	 *
	 */
	public static class FileSystemException extends RuntimeException {

		private static final long serialVersionUID = 4036134139372708185L;
		private final String fileName;
		private final String __reason;

		public FileSystemException(File file, String reason) { this(file.getPath(), reason); }

		public FileSystemException(String str, String reason) {
			fileName = str;
			this.__reason = reason;
		}

		public String getError() {
			return "Failed to access, create or write to " + fileName + ". Reason: " + __reason;
		}

	}

}
