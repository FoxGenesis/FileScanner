package net.foxgenesis.filescanner.loud;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import net.foxgenesis.watame.util.PrefixedThreadFactory;
import net.foxgenesis.watame.util.discord.AttachmentData;

public class QTFSSubscriber extends LoudScanner implements Closeable {
	private final ExecutorService executor = Executors.newCachedThreadPool(new PrefixedThreadFactory("Video Reader"));

	private final Path ffmpeg;
	private final Path quickTimeBinaryPath;

	public QTFSSubscriber(Path ffmpeg, Path qtfs) {
		this.ffmpeg = Objects.requireNonNull(ffmpeg);
		this.quickTimeBinaryPath = Objects.requireNonNull(qtfs);
	}

	@Override
	protected List<Double> processAttachment(AttachmentData data) {
		CompletableFuture<Void> write = null;
		try {
			List<Process> pipes = ProcessBuilder.startPipeline(Arrays.asList(
					new ProcessBuilder(this.quickTimeBinaryPath.toString(), "-q").redirectOutput(Redirect.PIPE),
					new ProcessBuilder(ffmpeg.toString(), "-hide_banner", "-nostats", "-i", "-", "-af",
							"ebur128" /* + "=scale=relative:target=-10" */, "-f", "null", "-")
							.redirectInput(Redirect.PIPE)));

			try (InputStream in = data.openConnection();
					BufferedReader pErr = pipes.get(pipes.size() - 1).errorReader()) {
				// Asynchronous write thread
				write = CompletableFuture.runAsync(() -> {
					try (in; OutputStream out = pipes.get(0).getOutputStream()) {
						in.transferTo(out);
					} catch (IOException e) {
						pipes.forEach(p -> p.destroyForcibly().onExit().join());
						throw new CompletionException(e);
					}
				}, executor).orTimeout(15, TimeUnit.SECONDS);

				return getLUValues(pErr.lines());

			} finally {
				pipes.forEach(p -> {
					if (p.isAlive())
						p.destroyForcibly().onExit().join();
				});
			}
		} catch (Exception e) {
			throw new CompletionException("Error while processing attachment: " + data.getFileName(), e);
		} finally {
			if (write != null)
				write.join();
		}
	}

	@Override
	public void close() {
		executor.shutdown();
	}
}
