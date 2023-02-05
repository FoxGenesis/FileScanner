package net.foxgenesis.watame.filescanner.scanner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.foxgenesis.watame.filescanner.scanner.AttachmentScanner.AttachmentException;

/**
 * A class used to manage a list of {@link AttachmentScanner AttachmentScanners}
 * and execute all attachment testing asynchronously.
 * 
 * @author Ashley
 *
 */
public class AttachmentManager {
	/**
	 * Logger
	 */
	protected static final Logger logger = LoggerFactory.getLogger("AttachmentManager");

	/**
	 * List of all scanners
	 */
	private final List<AttachmentScanner> scanners = new ArrayList<>();

	/**
	 * Create a new instance.
	 */
	public AttachmentManager() {}

	/**
	 * Add an {@link AttachmentScanner} to this manager.
	 * 
	 * @param scanner - the scanner to add
	 * @return Returns {@code false} if the {@code scanner} is {@code null} or the
	 *         {@code scanner} is already added. Otherwise, returns {@code true}.
	 */
	public boolean addScanner(AttachmentScanner scanner) {
		return scanner == null ? false : scanners.contains(scanner) ? false : scanners.add(scanner);
	}

	/**
	 * Test an {@link Attachment} with all added {@link AttachmentScanner
	 * AttachmentScanners} asynchronously.
	 * 
	 * @param in         - attachment data
	 * @param msg        - the message this attachment is from
	 * @param attachment - the attachment to scan
	 * @return A {@link CompletableFuture} that will complete exceptionally with an
	 *         {@link AttachmentException} if thrown by any
	 *         {@link AttachmentScanner}
	 */
	public CompletableFuture<Void> testAttachment(byte[] in, Message msg, Attachment attachment) {
		return (in == null || in.length == 0)
				? CompletableFuture.failedFuture(new NullPointerException("Attachment data was null"))
				: transformData(in, msg, attachment)
						.thenComposeAsync(newData -> runScannersAsync(newData, msg, attachment));
	}

	/**
	 * Transform attachment data before submitting it to the scanners.
	 * 
	 * @param in         - attachment data
	 * @param msg        - the message this attachment is from
	 * @param attachment - he attachment to scan
	 * @return A {@link CompletableFuture} that will transform the attachment data
	 */
	protected CompletableFuture<byte[]> transformData(byte[] in, Message msg, Attachment attachment) {
		return CompletableFuture.completedFuture(in);
	}

	/**
	 * Run all {@link AttachmentScanner AttachmentScanners} asynchronously and pass
	 * on any {@link AttachmentException} to the returned {@link CompletableFuture}.
	 * 
	 * @param in         - attachment data
	 * @param msg        - the message this attachment is from
	 * @param attachment - the attachment to scan
	 * @return A {@link CompletableFuture} that will complete exceptionally with an
	 *         {@link AttachmentException} if any {@link AttachmentScanner}
	 *         completes with one
	 * @author Ashley
	 */
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
}
