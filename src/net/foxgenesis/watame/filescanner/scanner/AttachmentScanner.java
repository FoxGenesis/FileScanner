package net.foxgenesis.watame.filescanner.scanner;

import java.io.File;
import java.util.concurrent.CompletableFuture;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;

@FunctionalInterface
public interface AttachmentScanner {
	public CompletableFuture<Void> testAttachment(byte[] in, Message msg, Attachment attachment) throws AttachmentException;

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
	public static class OperatingSystemException extends RuntimeException {
		
		private static final long serialVersionUID = -1920850180721778217L;
		private String os_name;
		
		public OperatingSystemException(String os) {os_name = os;}
		
		String getReason() {return "Operating System \"" + os_name + "\" is not supported";}
		
	}
	
	/**
	 * @author Spaz-Master
	 *
	 */
	public static class FileSystemException extends RuntimeException {
		
		private static final long serialVersionUID = 4036134139372708185L;
		private String fileName;
		private String __reason;
		public FileSystemException(File file, String reason) {
			fileName = file.getPath();
			this.__reason = reason;
		}
		public FileSystemException(String str, String reason) {
			fileName = str;
			this.__reason = reason;
		}
		
		String getError() {return "Failed to access, create or write to " + fileName + ". Reason: " + __reason;}
		
	}
	
}
