package net.foxgenesis.watame.filescanner.scanner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.Message.Attachment;

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
	 * Asynchronous executor
	 */
	protected final Executor executor;

	/**
	 * Create a new instance.
	 */
	public AttachmentManager() {
		this(ForkJoinPool.commonPool());
	}

	/**
	 * Create a new instance using the provided {@link Executor}.
	 * 
	 * @param executor - the service used to submit asynchronous tasks
	 */
	public AttachmentManager(Executor executor) {
		this.executor = Objects.requireNonNull(executor);
	}

	/**
	 * Add an {@link AttachmentScanner} to this manager.
	 * 
	 * @param scanner - the scanner to add
	 * 
	 * @return Returns {@code false} if the {@code scanner} is {@code null} or the
	 *         {@code scanner} is already added. Otherwise, returns {@code true}.
	 */
	public boolean addScanner(AttachmentScanner scanner) {
		return scanner == null ? false : this.scanners.contains(scanner) ? false : this.scanners.add(scanner);
	}

	/**
	 * Test an {@link Attachment} with all added {@link AttachmentScanner
	 * AttachmentScanners} asynchronously.
	 * 
	 * @param data       - attachment data
	 * @param msg        - the message this attachment is from
	 * @param attachment - the attachment to scan
	 * 
	 * @return A {@link CompletableFuture} that will complete exceptionally with an
	 *         {@link AttachmentException} if thrown by any
	 *         {@link AttachmentScanner}
	 * 
	 * @throws IOException
	 */
	public CompletableFuture<Void> testAttachment(byte[] data, AttachmentData attachment) {
		return (data == null || data.length == 0)
				? CompletableFuture.failedFuture(new NullPointerException("Attachment data was null"))
				: CompletableFuture.completedFuture(data)
						.thenComposeAsync(in -> transformData(in, attachment), executor)
						.thenComposeAsync(newData -> runScannersAsync(newData, attachment), executor);
	}

	/**
	 * Transform attachment data before submitting it to the scanners.
	 * 
	 * @param in         - attachment data
	 * @param msg        - the message this attachment is from
	 * @param attachment - he attachment to scan
	 * 
	 * @return A {@link CompletableFuture} that will transform the attachment data
	 */
	@SuppressWarnings("static-method")
	protected CompletableFuture<byte[]> transformData(byte[] in, AttachmentData attachment) {
		return CompletableFuture.completedFuture(in);
	}

	/**
	 * Run all {@link AttachmentScanner AttachmentScanners} asynchronously and pass
	 * on any {@link AttachmentException} to the returned {@link CompletableFuture}.
	 * 
	 * @param in         - attachment data
	 * @param msg        - the message this attachment is from
	 * @param attachment - the attachment to scan
	 * 
	 * @return A {@link CompletableFuture} that will complete exceptionally with an
	 *         {@link AttachmentException} if any {@link AttachmentScanner}
	 *         completes with one
	 */
	private CompletableFuture<Void> runScannersAsync(byte[] in, AttachmentData attachment) {
		return CompletableFuture.allOf(this.scanners.stream().filter(scanner -> scanner.shouldTest(attachment))
				.map(scanner -> scanner.testAttachment(in, attachment, executor)).toArray(CompletableFuture[]::new));
	}

	/**
	 * Check if this manager will scan an attachment.
	 * 
	 * @param attachment - {@link AttachmentData} to check
	 * 
	 * @return Returns {@code true} if this manager will scan the provided
	 *         {@link AttachmentData}
	 */
	public boolean canScan(AttachmentData attachment) {
		return this.scanners.stream().anyMatch(scanner -> scanner.shouldTest(attachment));
	}
}
