package net.foxgenesis.watame.filescanner.scanner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.internal.utils.IOUtil;
import net.foxgenesis.watame.filescanner.FileScannerPlugin;

/**
 * Attachment scanner that detects loud videos.
 * 
 * @author Spaz-Master, Ashley
 *
 */
public class LoudVideoDetection implements AttachmentScanner {
	private static final double LOUDNESS_PERCENT = 0.2;
	private static final int TIMEOUT_VALUE = 2;
	private static final TimeUnit TIMEOUT_UNIT = TimeUnit.MINUTES;
	/**
	 * length of EBUR128 tag in ffmpeg
	 */
	private static final int EBUR128 = 35;
	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger("LoudVideoDetection");

	/**
	 * Called by the Testing subscriber-and-publisher.
	 * 
	 * @author Spaz-Master
	 * @throws CompletionException - if detected a loud video
	 * @param in         - the bytes of the attachment
	 * @param msg        - the message object of the discord message
	 * @param attachment - the attachment object of the message to scan
	 */
	@Override
	public CompletableFuture<Void> testAttachment(byte[] in, Message msg, Attachment attachment) {
		CompletableFuture<List<Double>> futureSegments;
		try {
			futureSegments = getVolumeSegments(in);
		} catch (IOException e) {
			logger.error("Error while getting segments", e);
			return CompletableFuture.failedFuture(e);
		}

		CompletableFuture<Integer> segmentSize = futureSegments.copy().thenApply(List::size);

		// I have no idea if this will actually work ~ Ashley
		CompletableFuture<Void> out = new CompletableFuture<>() {
			@Override
			public boolean cancel(boolean term) {
				futureSegments.cancel(term);
				return super.cancel(term);
			}
		};

		futureSegments.thenApplyAsync(segments -> {
			int strikes = 0;
			ArrayList<Integer> strikeChunks = new ArrayList<>();
			/*
			 * sometimes a video could have a loud peak for less than a second, possibly due
			 * to random noise or encoding error. This acts as a sort of "forgiveness meter"
			 * so that it takes more than a one-time detection of loud audio
			 */
			for (double value : segments)
				if (value > -2) {
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
		}).thenAcceptBothAsync(segmentSize, (strikeChunks, total) -> {
			for (int strikeChunk : strikeChunks) {
				// if we have a time period of loudness that is bigger than 1/5th of the total
				// video,
				// then we triggered loudness detection
				if ((double) strikeChunk / (double) total >= LOUDNESS_PERCENT) {
					logger.debug("Detected loud video");
					out.completeExceptionally(new AttachmentException(FileScannerPlugin.LOUD_VIDEO));
					return;
				}

			}
			out.complete(null);
		}).exceptionally(err -> {
			out.completeExceptionally(err);
			return null;
		});
		return out;
	}

	/**
	 * Extracts the data of the average volume in accordance to EBUR 128 standard of
	 * the attachment.
	 * 
	 * @author Ashley, Spaz-Master
	 * @param buffer byte array of the attachment
	 * @return ArrayList of Doubles of read volume average values
	 * @throws IOException if some kind of processing error occured with FFMPEG
	 */
	private CompletableFuture<List<Double>> getVolumeSegments(byte[] buffer) throws IOException {
		Process p = new ProcessBuilder("ffmpeg", "-hide_banner", "-nostats", "-i", "-", "-filter_complex", "ebur128",
				"-f", "null", "-").start();
		CompletableFuture<String> cf = CompletableFuture.supplyAsync(() -> {
			try {
				return new String(p.getErrorStream().readAllBytes());
			} catch (IOException e) {
				// unfortunately, if interupted then all our data is lost
				logger.error("Reading from ffmpeg was interrupted: ", e);
				throw new CompletionException(e);
			} finally {
				IOUtil.silentClose(p.getErrorStream());
			}
		}, FileScannerPlugin.SCANNING_POOL).orTimeout(TIMEOUT_VALUE, TIMEOUT_UNIT);

		try {
			p.getOutputStream().write(buffer);
		} catch (IOException ex) {
			if (!ex.getMessage().equals("Broken pipe"))
				// The Funny™
				throw new CompletionException(ex);
		} finally {
			try {
				p.getOutputStream().flush();
				p.getOutputStream().close();
			} catch (IOException e) {
				throw new CompletionException(e);
			}
		}

		return cf.thenApplyAsync(str -> {
			String[] results = str.split("\n");
			if (results == null || results.length < 2)
				return null;

			List<Double> output = new ArrayList<>();
			for (String tmp : results) {
				if (tmp.startsWith("[Parsed_ebur128_0")) {
					int start = tmp.indexOf("M:", EBUR128) + 2;
					if (start < 2)
						continue;
					int end = tmp.indexOf("S:", start);

					String loudStr = tmp.substring(start, end);

					try {
						double val = Double.parseDouble(loudStr);
						output.add(val);
					} catch (NumberFormatException ex) {
						logger.warn("Bad double value " + loudStr + " skipping...");
						break;
					}

				}

			}
			return output;
		}).whenComplete((list, err) -> {
			p.destroy();
			if (err != null)
				throw new CompletionException(err);
		});
	}

}
