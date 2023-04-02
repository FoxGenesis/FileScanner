package net.foxgenesis.watame.filescanner.scanner.scanners;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.foxgenesis.watame.filescanner.FileScannerPlugin;
import net.foxgenesis.watame.filescanner.scanner.AttachmentData;
import net.foxgenesis.watame.filescanner.scanner.AttachmentException;
import net.foxgenesis.watame.filescanner.scanner.AttachmentScanner;

/**
 * Attachment scanner that detects loud videos.
 * 
 * @author Spaz-Master, Ashley
 *
 */
public class LoudVideoDetectionTest implements AttachmentScanner {
	/**
	 * Threshold for strike chunks
	 */
	private static final double LOUDNESS_THRESHOLD = -2;

	/**
	 * Loudness threshold for loud videos
	 */
	private static final double LOUDNESS_PERCENT = 0.2;

	/**
	 * Timeout for FFMPEG
	 */
	private static final int TIMEOUT_VALUE = 15;

	/**
	 * Timeout unit for FFMPEG
	 */
	private static final TimeUnit TIMEOUT_UNIT = TimeUnit.SECONDS;

	/**
	 * length of EBUR128 tag in ffmpeg
	 */
	private static final int EBUR128 = 35;

	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(LoudVideoDetectionTest.class);

	/**
	 * Predicate to skip testing of an attachment
	 */
	private static final Predicate<String> pattern = Pattern.compile("\\b(loud|ear rape)\\b", Pattern.CASE_INSENSITIVE)
			.asPredicate();

	/**
	 * Called by the Testing subscriber-and-publisher.
	 * 
	 * @author Spaz-Master
	 * 
	 * @throws CompletionException - if detected a loud video
	 * 
	 * @param in         - the bytes of the attachment
	 * @param attachment - the attachment object of the message to scan
	 * @param executor   - asynchronous task executor
	 */
	@Override
	public CompletableFuture<Void> testAttachment(byte[] in, AttachmentData attachment, Executor executor) {
		try {
			long startTime = System.currentTimeMillis();

			CompletableFuture<List<Double>> getSegments = getFFMPEGData(in, executor).thenApplyAsync(this::getSegments,
					executor);
			CompletableFuture<Integer> totalSegments = getSegments.thenApplyAsync(List::size, executor);

			return getSegments.thenApplyAsync(segments -> getStrikeChunks(segments, LOUDNESS_THRESHOLD), executor)
					.thenAcceptBothAsync(totalSegments, (strikeChunks, total) -> {
						for (int strikeChunk : strikeChunks) {
							// if we have a time period of loudness that is bigger than 1/5th of the total
							// video,
							// then we triggered loudness detection
							if ((double) strikeChunk / (double) total >= LOUDNESS_PERCENT) {
								logger.debug("Detected loud video {} / {} > {}", strikeChunk, total, LOUDNESS_PERCENT);
								throw new CompletionException(
										new AttachmentException(FileScannerPlugin.ScanResult.LOUD_VIDEO));
							}
						}

						long end = System.currentTimeMillis();
						logger.trace("EBUR128 for [{}]: {} / {} < {}", strikeChunks, total, LOUDNESS_PERCENT);
						logger.debug(
								"EBUR128 for [{}] completed in %,.2f sec(s) {} / {} < {}"
										.formatted((end - startTime) / 1_000D),
								attachment.getFileName(), strikeChunks, total, LOUDNESS_PERCENT);
					}, executor);
		} catch (IOException e) {
			logger.error("Error while getting segments", e);
			return CompletableFuture.failedFuture(e);
		}
	}

	/**
	 * Extracts the data of the average volume in accordance to EBUR 128 standard of
	 * the attachment.
	 * 
	 * @author Ashley, Spaz-Master
	 * 
	 * @param buffer   byte array of the attachment
	 * @param executor
	 * 
	 * @return ArrayList of Doubles of read volume average values
	 * 
	 * @throws IOException if some kind of processing error occured with FFMPEG
	 */
	private static CompletableFuture<byte[]> getFFMPEGData(byte[] buffer, Executor executor) throws IOException {
		ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-hide_banner", "-nostats", "-i", "-", "-af", "ebur128", "-f",
				"null", "-");

		Process p = pb.start();

		CompletableFuture<byte[]> futureData = new CompletableFuture<>();
		futureData.completeAsync(() -> {
			try (BufferedInputStream in = new BufferedInputStream(p.getErrorStream())) {
				return in.readAllBytes();
			} catch (IOException e) {
				throw new CompletionException(e);
			}
		}, executor);

		try (BufferedOutputStream out = new BufferedOutputStream(p.getOutputStream())) {
			out.write(buffer);
		} catch (IOException e) {
			futureData.completeExceptionally(e);
		}

		return futureData.orTimeout(TIMEOUT_VALUE, TIMEOUT_UNIT).whenCompleteAsync((data, err) -> {
			if (err != null) {
				p.destroyForcibly();
			}
		}, executor);
	}

	/**
	 * Extracts the EBUR 128 data from FFMPEG output into a list of loudness points.
	 * 
	 * @param in - FFMPEG error output
	 * 
	 * @return Returns a {@link List} of numbers representing loudness
	 */
	private List<Double> getSegments(byte[] in) {
		return Arrays.stream(new String(in).split("\n")).filter(s -> s.startsWith("[Parsed_ebur128_0")).map(s -> {
			int start = s.indexOf("M:", EBUR128) + 2;
			if (start < 2)
				return Double.NaN;
			int end = s.indexOf("S:", start);

			String loudStr = s.substring(start, end);

			try {
				return Double.parseDouble(loudStr);
			} catch (NumberFormatException ex) {
				logger.warn("Bad double value " + loudStr + " skipping...");
				return Double.NaN;
			}
		}).filter(d -> d != Double.NaN).toList();
	}

	/**
	 * Convert EBUR 128 segments into chunks of loudness.
	 * 
	 * @param segments - EBUR 128 segments
	 * 
	 * @return Returns a {@link List} of chunks
	 */
	private static List<Integer> getStrikeChunks(List<Double> segments, double threshold) {
		ArrayList<Integer> strikeChunks = new ArrayList<>();
		double thresh = -Math.abs(threshold);
		int strikes = 0;
		/*
		 * sometimes a video could have a loud peak for less than a second, possibly due
		 * to random noise or encoding error. This acts as a sort of "forgiveness meter"
		 * so that it takes more than a one-time detection of loud audio
		 */
		for (double value : segments)
			if (value > thresh) {
				// if the loudness value is greater than -4.5
				strikes++;
			} else if (strikes > 0) {
				// otherwise, we have gone back to a segment that isnt loud anymore and we can
				// add a group of loud chunks back into a
				// strike cache
				strikeChunks.add(strikes);
				strikes = 0;
			}
		// end for loop

		if (strikes > 0) {
			// if video ended with loud strikes, then add those chunks as well
			strikeChunks.add(strikes);
		}

		return strikeChunks;
	}

	@Override
	public boolean shouldTest(AttachmentData attachment) {
		return attachment.isVideo()
				&& !pattern.test(attachment.message.getContentRaw().replaceAll("\\|\\|.*?\\|\\|", ""));
	}
}
