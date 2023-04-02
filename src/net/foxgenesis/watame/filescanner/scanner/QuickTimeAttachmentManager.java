package net.foxgenesis.watame.filescanner.scanner;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

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
	 * 
	 * @throws FileNotFoundException    if the provided path does not exist
	 * @throws IllegalArgumentException if the provided path is a directory or not
	 *                                  executable
	 */
	public QuickTimeAttachmentManager(Path qtFastStartBinaryPath) throws IOException, FileNotFoundException {
		this(qtFastStartBinaryPath, ForkJoinPool.commonPool());
	}

	/**
	 * Create a new instance with the provided <em>QuickTime-FastStart</em> binary
	 * path and asynchronous task facility.
	 * 
	 * @param qtFastStartBinaryPath - path to the <em>QuickTime-FastStart</em>
	 *                              binary
	 * @param executor              - asynchronous task executor
	 * 
	 * @throws FileNotFoundException    if the provided path does not exist
	 * @throws IllegalArgumentException if the provided path is a directory or not
	 *                                  executable
	 */
	public QuickTimeAttachmentManager(Path qtFastStartBinaryPath, Executor executor)
			throws IOException, FileNotFoundException {
		super(executor);

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
	protected CompletableFuture<byte[]> transformData(byte[] in, AttachmentData attachment) {
		return (attachment.isVideo() && !attachment.getFileExtension().equals("webm")
				? CompletableFuture.supplyAsync(() -> formatToQT(in, attachment), executor)
				: CompletableFuture.completedFuture(in));
	}

	/**
	 * Converts inputed QuickTime files (MOV, MP4) into fast-start format.
	 * 
	 * @author Spaz-Master
	 * 
	 * @param input - input data of file to convert to QuickTime fast-start format,
	 * 
	 * @return - the newly fast-start format file
	 */
	private byte[] formatToQT(byte[] input, AttachmentData attachment) {
		long start = System.currentTimeMillis();
		Process p = null;
		try {
			ProcessBuilder builder = new ProcessBuilder(this.quickTimeBinaryPath.toString(), "-q");

			p = builder.start();
			
			try (OutputStream out = p.getOutputStream()) {
				out.write(input);
				out.flush();
			}

			try (InputStream in = p.getInputStream()) {
				input = in.readAllBytes();
			}
		} catch (IOException e) {
			throw new CompletionException(e);
		} finally {
			if (p != null && p.isAlive())
				p.destroy();
		}

		logger.debug("Formatted [{}] to QT in %,.2f sec(s)".formatted((System.currentTimeMillis() - start) / 1_000D),
				attachment.getFileName());
		return input;
	}

	private static boolean isQTLibraryValid(Path path) throws IOException {
		Process p = null;
		try {
			p = new ProcessBuilder(path.toString(), "-v").start();
			try (InputStream in = p.getInputStream()) {
				logger.info("QuickTime-FastStart library version: {}",
						new String(in.readAllBytes()).split("\n", 2)[0].trim());
			}
			return true;
		} finally {
			if (p != null)
				p.destroy();
		}
	}
}
