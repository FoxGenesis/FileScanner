package net.foxgenesis.io;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class AsyncProcessInputStream extends ProcessInputStream {

	private volatile Throwable writeErr;
	private final Thread writeThread;

	public AsyncProcessInputStream(InputStream in, ProcessBuilder builder) throws IOException {
		this(in, builder, false);
	}

	public AsyncProcessInputStream(InputStream in, ProcessBuilder builder, boolean useError) throws IOException {
		super(in, builder, useError);
		 writeThread = new Thread(() -> {
				try (BufferedInputStream bIn = new BufferedInputStream(in) ;OutputStream out = process.getOutputStream()) {
					bIn.transferTo(out);
					out.flush();
				} catch (IOException e) {
					writeErr = e;
				}
				wrote = true;
//				try {
//					super.checkWrite();
//				} catch (IOException e) {
//					writeErr = e;
//				}
			}, "Async Process Write");
		writeThread.setUncaughtExceptionHandler((t, err) -> { writeErr = err; });
		writeThread.start();
	}
	
	@Override
	public long checkWrite() {return 0;}

	@Override
	protected void checkErr() throws IOException {
		if (writeErr != null) {
			synchronized (writeErr) {
				if (writeErr != null) {
					Throwable e = writeErr;
					writeErr = null;
					writeThread.interrupt();
					throw new IOException("Error in write thread", e);
				}
			}
		}
	}

	@Override
	public void close() throws IOException {
		if (writeThread.isAlive()) {
			writeThread.interrupt();
			try {
				writeThread.join();
			} catch (InterruptedException e) {
				throw new IOException("Interrupted while closing write thread", e);
			}
		}
		super.close();
	}
}
