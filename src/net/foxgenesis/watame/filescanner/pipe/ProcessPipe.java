package net.foxgenesis.watame.filescanner.pipe;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public class ProcessPipe implements Pipe {
	private static int ID = 0;

	protected final CompletableFuture<Void> onExit = new CompletableFuture<>();
	protected final PipedOutputStream out = new PipedOutputStream();
	protected final ProcessBuilder builder;
	protected final boolean useError;
	protected final InputStream in;
	protected final Executor executor;
	private final Pipe parent;
	public final int id;

	public ProcessPipe(ProcessBuilder builder, InputStream in) { this(builder, false, null, in); }

	public ProcessPipe(ProcessBuilder builder, Executor executor, InputStream in) {
		this(builder, false, executor, in);
	}

	public ProcessPipe(ProcessBuilder builder, boolean useError, Executor executor, InputStream in) {
		this.builder = Objects.requireNonNull(builder);
		this.useError = useError;
		this.in = Objects.requireNonNull(in);
		this.executor = Objects.requireNonNullElse(executor, ForkJoinPool.commonPool());
		this.parent = null;
		this.id = ++ID;
	}

	public ProcessPipe(ProcessBuilder builder, Pipe parent) throws IOException { this(builder, false, null, parent); }

	public ProcessPipe(ProcessBuilder builder, Executor executor, Pipe parent) throws IOException {
		this(builder, false, executor, parent);
	}

	public ProcessPipe(ProcessBuilder builder, boolean useError, Executor executor, Pipe parent) throws IOException {
		this.builder = Objects.requireNonNull(builder);
		this.executor = Objects.requireNonNullElse(executor, ForkJoinPool.commonPool());
		this.parent = Objects.requireNonNull(parent);
		this.useError = useError;
		this.in = parent.getConnection();
		this.id = ++ID;
	}

	@Override
	public PipedInputStream getConnection() throws IOException { return new PipedInputStream(out); }

	@Override
	public CompletableFuture<Void> onExit() {
		return parent != null ? parent.onExit().thenCompose(v -> onExit) : onExit;
	}

	protected synchronized void startPipe() throws IOException {
		if (parent != null)
			parent.pipeData();
	}

	@Override
	public synchronized void pipeData() throws IOException {
		startPipe();

		Process p = builder.start();
		CompletableFuture.runAsync(() -> {
			try (OutputStream o = p.getOutputStream(); in) {
				in.transferTo(o);
				o.flush();
			} catch (IOException e) {
				p.destroy();
				throw new CompletionException("Error in process pipe id: " + id, e);
			}

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
		}, executor).whenComplete((v, err) -> {
			if (err != null)
				onExit.completeExceptionally(err);
			else
				onExit.complete(null);
		});;
	}

	@Override
	public void close() throws Exception {
		if (parent != null)
			parent.close();
		onExit().join();
	}
}
