package net.foxgenesis.watame.filescanner.scanner;
public class AttachmentException extends RuntimeException {
	private static final long serialVersionUID = 5616757818200082472L;

	/**
	 * The exception id
	 */
	public final int id;

	public AttachmentException(int id) { this.id = id; }
}