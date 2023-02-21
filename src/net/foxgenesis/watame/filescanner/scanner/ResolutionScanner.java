package net.foxgenesis.watame.filescanner.scanner;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.foxgenesis.watame.filescanner.FileScannerPlugin;

/**
 * 
 * @author Spaz-Master
 *
 */
public class ResolutionScanner implements AttachmentScanner {

	private static final Logger logger = LoggerFactory.getLogger("ResolutionScanner");
	private static final int TIMEOUT_VALUE = 2;
	private static final TimeUnit TIMEOUT_UNIT = TimeUnit.MINUTES;

	@Override
	public CompletableFuture<Void> testAttachment(byte[] in, Message msg, Attachment attachment) {
		if (!attachment.isVideo())
			return CompletableFuture.completedFuture(null);

		CompletableFuture<Void> out = new CompletableFuture<>();

		try {
			Process p = new ProcessBuilder("ffprobe", "-v", "error", "-show_entries", "frame=pkt_pts_time,width,height",
					"-select_streams", "v", "-of", "csv=p=0", "-i", "-").redirectErrorStream(true).start();

			CompletableFuture.supplyAsync(() -> {
				try {
					logger.debug("Reading all bytes");
					return new String(p.getInputStream().readAllBytes());
				} catch (IOException e) {
					// unfortunately, if interupted then all our data is lost
					logger.error("Reading from ffmpeg was interrupted: ", e);
					throw new CompletionException(e);
				} finally {
					p.destroy();
				}
			}).whenComplete((s, err) -> {
				logger.info("output: " + s);
				p.destroy();
				if (err != null)
					throw new CompletionException(err.getCause());
			}).thenApplyAsync(output -> {
				System.out.println(output);
				// the entire raw ffprobe string data has been obtained.
				// begin splitting it up
				String[] numbers = output.split("\n");
				// each line consists of the x coord, a comma, and a y coord per each frame.
				// split them up by lines
				String[] resString = numbers[0].split(",");
				// get the x coord and they y coord separately
				for (int i = 1; i < numbers.length; i++) {
					// while the frame picked up has an invalid timestamp, keep trying to
					// get the resolution. sometimes the first few frames are invalid
					if (!resString[0].equals("N/A"))
						break;
					resString = numbers[i].split(",");
				}
				if (resString.length != 3) {
					// if the current line doesnt match out x,y specifications, error
					logger.error("Unable to get initial resolution from piped input [{}]", output);
					throw new CompletionException(
							new UnsupportedOperationException("Unable to get initial resolution from piped input"));
				}
				return new String[][] { numbers, resString };
			}).thenAcceptAsync(arr -> {
				String[] numbers = arr[0];
				String[] resString = arr[1];

				int[] dimension = parseInt(resString, 1);
				int w = dimension[0];
				int h = dimension[1];

				if (Arrays.stream(numbers).filter(line -> !line.isBlank()).map(line -> line.split(","))
						.map(comp -> parseInt(comp, 1)).anyMatch(size -> !(w == size[0] && h == size[1])))
					out.completeExceptionally(new AttachmentException(FileScannerPlugin.CRASHER_VIDEO));
				else
					out.complete(null);
			}).orTimeout(TIMEOUT_VALUE, TIMEOUT_UNIT).exceptionally(err -> {
				out.completeExceptionally(err);
				return null;
			});
		} catch (Exception e) {
			out.completeExceptionally(e);
		}
		return out;
	}

	private static int[] parseInt(String[] arr, int offset) {
		int[] out = new int[arr.length - offset];
		for (int i = offset; i < arr.length; i++)
			out[i] = Integer.parseInt(arr[i]);
		return out;
	}

	@Override
	public boolean shouldTest(Message message, Attachment attachment) { return false; }
}
