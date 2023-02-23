package net.foxgenesis.watame.filescanner.scanner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;

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
	private static final int TIMEOUT_VALUE = 1;
	private static final TimeUnit TIMEOUT_UNIT = TimeUnit.MINUTES;
	/**
	 * length of EBUR128 tag in ffmpeg
	 */
	private static final int EBUR128 = 35;
	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger("LoudVideoDetection");

	private static final Predicate<String> pattern = Pattern.compile("\\b(loud|ear rape)\\b", Pattern.CASE_INSENSITIVE)
			.asMatchPredicate();

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
		CompletableFuture<Void> out = new CompletableFuture<>();

		try {
			getVolumeSegments(in, attachment.getFileName()).thenAcceptAsync(segments -> {
				int total = segments.size();
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
			}, FileScannerPlugin.SCANNING_POOL).exceptionally(err -> {
				out.completeExceptionally(err);
				return null;
			});
		} catch (IOException e) {
			logger.error("Error while getting segments", e);
			return CompletableFuture.failedFuture(e);
		}

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
	private CompletableFuture<List<Double>> getVolumeSegments(byte[] buffer, String filename) throws IOException {
		Process p = new ProcessBuilder("ffmpeg", "-hide_banner", "-nostats", "-i", "-", "-filter_complex", "ebur128",
				"-f", "null", "-").start();

		CompletableFuture<List<Double>> cf = CompletableFuture.supplyAsync(() -> {
			try {
				long startTime = System.currentTimeMillis();
				List<Double> output = new ArrayList<>();
				String tmp = null;

				while ((tmp = p.errorReader().readLine()) != null) {
					if (tmp.startsWith("[Parsed_ebur128_0")) {
						int start = tmp.indexOf("M:", EBUR128) + 2;
						if (start < 2)
							continue;
						int end = tmp.indexOf("S:", start);

						String loudStr = tmp.substring(start, end);

						try {
							output.add(Double.parseDouble(loudStr));
						} catch (NumberFormatException ex) {
							logger.warn("Bad double value " + loudStr + " skipping...");
						}
					}
				}
				logger.debug("Read all segments of [{}] in %,.2f sec(s)"
						.formatted((System.currentTimeMillis() - startTime) / 1_000D), filename);
				return output;
			} catch (IOException e) {
				logger.error("Reading from ffmpeg was interrupted: ", e);
				throw new CompletionException(e);
			} finally {
				IOUtil.silentClose(p.errorReader());
			}
		}, FileScannerPlugin.SCANNING_POOL).orTimeout(TIMEOUT_VALUE, TIMEOUT_UNIT).whenComplete((list, err) -> {
			if (p.isAlive())
				p.destroy();
			
			p.onExit().join();
			logger.debug("FFMPEG instance for [{}] closed", filename);
		});

		try {
			p.getOutputStream().write(buffer);
		} catch (IOException ex) {
			if (!ex.getMessage().equals("Broken pipe"))
				throw new CompletionException(ex);
			p.destroyForcibly();
		} finally {
			p.getOutputStream().flush();
			p.getOutputStream().close();
		}

		return cf;
	}

	@Override
	public boolean shouldTest(Message message, Attachment attachment) {
		return attachment.isVideo() && !pattern.test(message.getContentRaw().replaceAll("\\|\\|.*?\\|\\|", ""));
	}
}
