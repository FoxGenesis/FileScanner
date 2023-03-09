package net.foxgenesis.watame.filescanner.executor;

import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

public class FileScannerThreadFactory implements ThreadFactory {
	private final AtomicLong count = new AtomicLong(1);

	private final String prefix;

	private final boolean daemon;

	public FileScannerThreadFactory(String prefix) { this(prefix, true); }

	public FileScannerThreadFactory(String prefix, boolean daemon) {
		this.prefix = Objects.requireNonNull(prefix);
		this.daemon = daemon;
	}

	@Override
	public Thread newThread(Runnable r) {
		final Thread thread = new Thread(r, this.prefix + "-Worker " + this.count.getAndIncrement());
		thread.setDaemon(this.daemon);
		return thread;
	}

	public String getPrefix() { return this.prefix; }

	public boolean isDaemon() { return this.daemon; }
}
