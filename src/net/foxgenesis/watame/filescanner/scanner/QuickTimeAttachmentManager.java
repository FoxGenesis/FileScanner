package net.foxgenesis.watame.filescanner.scanner;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.foxgenesis.watame.filescanner.FileScannerPlugin;

/**
 * Class used to transform attachment video data into <em>QuickTime</em> format
 * before submission to the {@link AttachmentScanner AttachmentScanners}.
 * 
 * @author Ashley
 *
 */
public class QuickTimeAttachmentManager extends AttachmentManager {

	/**
	 * Path to the <em>QuickTime-FastStart</em> binary
	 */
	private final Path quickTimeBinaryPath;

	/**
	 * Create a new instance with the provided <em>QuickTime-FastStart</em> binary
	 * path.
	 * 
	 * @param qtFastStartBinaryPath - path to the <em>QuickTime-FastStart</em>
	 *                              binary
	 * @throws FileNotFoundException    if the provided path does not exist
	 * @throws IllegalArgumentException if the provided path is a directory or not
	 *                                  executable
	 */
	public QuickTimeAttachmentManager(Path qtFastStartBinaryPath) throws IOException, FileNotFoundException {
		// Check if the provided path is valid
		if (Files.notExists(qtFastStartBinaryPath, LinkOption.NOFOLLOW_LINKS))
			throw new FileNotFoundException(qtFastStartBinaryPath.toString() + " does not exist!");
		else if (Files.isDirectory(qtFastStartBinaryPath, LinkOption.NOFOLLOW_LINKS))
			throw new IllegalArgumentException("Path must point to a regular file!");
		else if (!Files.isExecutable(qtFastStartBinaryPath))
			throw new IllegalArgumentException("File is not executable!");
		
		isQTLibraryValid(qtFastStartBinaryPath);
		
		this.quickTimeBinaryPath = Objects.requireNonNull(qtFastStartBinaryPath);
	}

	@Override
	protected CompletableFuture<byte[]> transformData(byte[] in, Message msg, Attachment attachment) {
		return (attachment.isVideo() && !attachment.getFileExtension().equals("webm")
				? CompletableFuture.supplyAsync(() -> formatToQT(in), FileScannerPlugin.SCANNING_POOL)
				: CompletableFuture.completedFuture(in));
	}

	/**
	 * Converts inputed QuickTime files (MOV, MP4) into fast-start format.
	 * 
	 * @author Spaz-Master
	 * @param input - input data of file to convert to QuickTime fast-start format,
	 * @return - the newly fast-start format file
	 */
	private byte[] formatToQT(byte[] input) {
		Process p = null;
		try {
			p = new ProcessBuilder(quickTimeBinaryPath.toString(), "-q").start();
			p.getOutputStream().write(input);
			p.getOutputStream().flush();
			p.getOutputStream().close();
			input = p.getInputStream().readAllBytes();
		} catch (IOException e) {
			logger.error("Failed to start Qt-FastStart binary: ", e);
		} finally {
			if (p != null)
				p.destroy();
		}

		return input;
	}

	private static boolean isQTLibraryValid(Path path) throws IOException {
		Process p = null;
		try {
			p = new ProcessBuilder(path.toString(), "-v").start();
			logger.info("QuickTime-FastStart library version: "
					+ new String(p.getInputStream().readAllBytes()).split("\n", 2)[0]);
			return true;
		} finally {
			if (p != null)
				p.destroy();
		}
	}
}
