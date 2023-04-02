package net.foxgenesis.watame.filescanner.pipe;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public class DuplicatePipe implements Pipe {
	private final Set<PipedOutputStream> outs = new HashSet<>();
	private CompletableFuture<Void> onExit;
	private final Executor executor;
	private final InputStream in;
	private final Pipe parent;

	public DuplicatePipe(InputStream in) { this(in, null); }

	public DuplicatePipe(InputStream in, Executor executor) {
		this.in = Objects.requireNonNull(in);
		this.executor = Objects.requireNonNullElse(executor, ForkJoinPool.commonPool());
		this.parent = null;
	}

	public DuplicatePipe(Pipe parent) throws IOException { this(parent, null); }

	
	public DuplicatePipe(Pipe parent, Executor executor) throws IOException {
		this.parent = Objects.requireNonNull(parent);
		this.executor = Objects.requireNonNullElse(executor, ForkJoinPool.commonPool());
		this.in = parent.getConnection();
	}

	@Override
	public PipedInputStream getConnection() throws IOException {
		if (onExit != null)
			throw new UnsupportedOperationException("Data pipe is already running!");
		synchronized (outs) {
			PipedOutputStream o = new PipedOutputStream();
			outs.add(o);
			return new PipedInputStream(o);
		}
	}

	@Override
	public synchronized void pipeData() throws IOException {
		if (parent != null)
			parent.pipeData();

		onExit = CompletableFuture.runAsync(() -> {
			byte[] buffer = new byte[1024];
			synchronized (outs) {
				try (in) {
					while (in.read(buffer) != -1)
						for (PipedOutputStream o : outs)
							o.write(buffer);
					
//					int b;
//					while ((b = in.read()) != -1)
//						for (PipedOutputStream o : outs)
//							o.write(b);
				} catch (IOException e) {
					throw new CompletionException(e);
				} finally {
					for (PipedOutputStream out : outs) {
						try {
							out.flush();
							out.close();
						} catch (IOException e) {
							throw new CompletionException(e);
						}
					}
				}
			}
		}, executor).whenComplete((v, err) -> {
			try {
				in.close();
			} catch (IOException e) {
				throw new CompletionException(e);
			}
		});
	}

	@Override
	public void close() throws Exception {
		if (parent != null)
			parent.close();

		onExit().join();
	}

	@Override
	public CompletableFuture<Void> onExit() {
		return parent != null ? parent.onExit().thenCompose(v -> onExit) : onExit;
	}
}
