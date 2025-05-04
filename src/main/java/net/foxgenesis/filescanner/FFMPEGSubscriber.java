package net.foxgenesis.filescanner;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import net.foxgenesis.watame.util.PrefixedThreadFactory;
import net.foxgenesis.watame.util.discord.AttachmentData;

public class FFMPEGSubscriber extends LoudScanner implements Closeable {
	private final ExecutorService executor = Executors.newCachedThreadPool(new PrefixedThreadFactory("Video Reader"));
	
	@Override
	protected List<Double> processAttachment(AttachmentData attachment) {
		CompletableFuture<Void> write = null;
		Process p = null;
		try {
			p = new ProcessBuilder("ffmpeg", "-hide_banner", "-nostats", "-i", "-", "-af",
					"ebur128" /* + "=scale=relative:target=-10" */, "-f", "null", "-").start();

			try (InputStream in = attachment.openConnection(); BufferedReader pErr = p.errorReader()) {
				// Asynchronous write thread
				final Process pp = p;
				write = CompletableFuture.runAsync(() -> {
					try (in; OutputStream out = pp.getOutputStream()) {
						in.transferTo(out);
					} catch (IOException e) {
						pp.destroyForcibly().onExit().join();
						throw new CompletionException(e);
					}
				}, executor).orTimeout(15, TimeUnit.SECONDS);

				return getLUValues(pErr.lines());
			}
		} catch (Exception e) {
			throw new CompletionException("Error while processing attachment: " + attachment.getFileName(), e);
		} finally {
			if (p != null && p.isAlive())
				p.destroyForcibly();
			if (write != null)
				write.join();
		}
	}
	
	@Override
	public void close() {
		executor.shutdown();
	}
}
