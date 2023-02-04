package net.foxgenesis.watame.filescanner.scanner;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.foxgenesis.watame.filescanner.FileScannerPlugin;
import net.foxgenesis.watame.filescanner.scanner.AttachmentScanner.AttachmentException;

/**
 * 
 * @author Ashley
 *
 */
public class AttachmentManager {
	private static final Logger logger = LoggerFactory.getLogger("AttachmentManager");

	private final List<AttachmentScanner> scanners = new ArrayList<>();
	private final Path qt_faststartBinary;

	public AttachmentManager(Path qt) throws FileNotFoundException {
		// Check if the provided path is valid
		if (Files.notExists(qt, LinkOption.NOFOLLOW_LINKS))
			throw new FileNotFoundException(qt.toString() + " does not exist!");
		else if (Files.isDirectory(qt, LinkOption.NOFOLLOW_LINKS))
			throw new IllegalArgumentException("Path must point to a regular file!");
		else if (!Files.isExecutable(qt))
			throw new IllegalArgumentException("File is not executable!");

		this.qt_faststartBinary = Objects.requireNonNull(qt);
	}

	public boolean addScanner(AttachmentScanner scanner) {
		return scanner == null ? false : scanners.contains(scanner) ? false : scanners.add(scanner);
	}

	public CompletableFuture<Void> testAttachment(byte[] in, Message msg, Attachment attachment) {
		if (in == null || in.length == 0)
			return CompletableFuture.failedFuture(new NullPointerException("Attachment data was null"));

		return (attachment.isVideo() && !attachment.getFileExtension().equals("webm")
				? CompletableFuture.supplyAsync(() -> formatToQT(in), FileScannerPlugin.SCANNING_POOL)
				: CompletableFuture.completedFuture(in))
				.thenComposeAsync(newData -> runScannersAsync(newData, msg, attachment));
	}

	private CompletableFuture<Void> runScannersAsync(byte[] in, Message msg, Attachment attachment) {
		CompletableFuture<Void> cf = new CompletableFuture<>();
		List<CompletableFuture<Void>> futures = Collections.synchronizedList(new ArrayList<>(scanners.size()));

		this.scanners.stream().map(scanner -> scanner.testAttachment(in, msg, attachment)).forEach(future -> {
			futures.add(future);
			future.exceptionallyAsync(err -> {
				if (err instanceof AttachmentException) {
					cf.completeExceptionally(err);
					synchronized (futures) {
						futures.forEach(f -> f.cancel(true));
						futures.clear();
					}
				}
				return null;
			});
		});

		return cf;
	}

	/**
	 * Converts inputted quicktime files (MOV, MP4) into faststart format
	 * 
	 * @author Spaz-Master
	 * @param input - input data of file to convert to quicktime faststart format,
	 * @return - the newly faststart format file
	 */
	private byte[] formatToQT(byte[] input) {
		Process p = null;
		try {
			p = new ProcessBuilder(qt_faststartBinary.toString(), "-q").start();
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
}
