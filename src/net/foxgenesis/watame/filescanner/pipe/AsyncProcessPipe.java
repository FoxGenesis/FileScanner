package net.foxgenesis.watame.filescanner.pipe;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

public class AsyncProcessPipe extends ProcessPipe {

	public AsyncProcessPipe(ProcessBuilder builder, boolean useError, Pipe parent) throws IOException {
		this(builder, useError, null, parent);
	}

	public AsyncProcessPipe(ProcessBuilder builder, boolean useError, Executor executor, Pipe parent)
			throws IOException {
		super(builder, useError, executor, parent);
	}

	public AsyncProcessPipe(ProcessBuilder processBuilder, InputStream openStream) {
		super(processBuilder, openStream);
	}

	@Override
	public synchronized void pipeData() throws IOException {
		startPipe();

		Process p = builder.start();
		CompletableFuture.allOf(CompletableFuture.runAsync(() -> {
			try (OutputStream o = p.getOutputStream(); in) {
				in.transferTo(o);
				o.flush();
			} catch (IOException e) {
				p.destroy();
				throw new CompletionException("Error in process pipe id: " + id, e);
			}
		}, executor), CompletableFuture.runAsync(() -> {
			try (out; InputStream pIn = p.getInputStream(); InputStream pErr = p.getErrorStream()) {
				if (useError)
					pErr.transferTo(out);
				else
					pIn.transferTo(out);
				out.flush();
			} catch (IOException e) {
				p.destroy();
				throw new CompletionException("Error in process pipe id: " + id, e);
			}
		}, executor), p.onExit()).whenComplete((v, err) -> {
			if (err != null)
				onExit.completeExceptionally(err);
			else
				onExit.complete(null);
		});
	}
}
