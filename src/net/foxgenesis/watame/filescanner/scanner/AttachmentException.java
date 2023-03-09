package net.foxgenesis.watame.filescanner.scanner;

import net.foxgenesis.watame.filescanner.FileScannerPlugin.ScanResult;

public class AttachmentException extends RuntimeException {
	private static final long serialVersionUID = 5616757818200082472L;

	/**
	 * The exception id
	 */
	public final ScanResult result;

	public AttachmentException(ScanResult result) { this.result = result; }
}