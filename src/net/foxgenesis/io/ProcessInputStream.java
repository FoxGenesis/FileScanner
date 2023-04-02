package net.foxgenesis.io;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

public class ProcessInputStream extends FilterInputStream {
	private static final int DEFAULT_BUFFER_SIZE = 8192;

	private final InputStream pIn;

	protected final Process process;

	protected boolean wrote = false;

	public ProcessInputStream(InputStream in, ProcessBuilder builder) throws IOException {
		this(in, builder, false);
	}

	@SuppressWarnings("resource")
	public ProcessInputStream(InputStream in, ProcessBuilder builder, boolean useError) throws IOException {
		super(in);
		this.process = Objects.requireNonNull(builder).start();
		this.pIn = new BufferedInputStream((useError ? process.getErrorStream() : process.getInputStream()));
//		this.pIn = new BufferedInputStream(process.getErrorStream());
//		CompletableFuture.runAsync(() -> {
//			try (in;OutputStream out = process.getOutputStream()) {
//				in.transferTo(out);
//				out.flush();
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
//
//			wrote = true;
//		});
	}

	@Override
	public int read() throws IOException {
		checkWrite();
		return pIn.read();
	}

	@Override
	public int read(byte[] b) throws IOException {
		checkWrite();
		return pIn.read(b);
	}

	@Override
	public int read(byte b[], int off, int len) throws IOException {
		checkWrite();
		return pIn.read(b, off, len);
	}

	@Override
	public long skip(long n) throws IOException {
		checkWrite();
		return pIn.skip(n);
	}

	@Override
	public int available() throws IOException {
		return pIn.available();
	}

	@Override
	public synchronized void mark(int readlimit) {
		pIn.mark(readlimit);
	}

	@Override
	public synchronized void reset() throws IOException {
		pIn.reset();
	}

	@Override
	public boolean markSupported() {
		return pIn.markSupported();
	}

	@Override
	public long transferTo(OutputStream out) throws IOException {
		checkWrite();
		Objects.requireNonNull(out, "out");
		long transferred = 0;
		byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
		int read;
		while ((read = pIn.read(buffer, 0, DEFAULT_BUFFER_SIZE)) >= 0) {
			out.write(buffer, 0, read);
			transferred += read;
		}
		return transferred;
	}

	@Override
	public byte[] readNBytes(int len) throws IOException {
		checkWrite();
		return pIn.readNBytes(len);
	}

	@Override
	public int readNBytes(byte[] b, int off, int len) throws IOException {
		checkWrite();
		return pIn.readNBytes(b, off, len);
	}

	@Override
	public byte[] readAllBytes() throws IOException {
		checkWrite();
		return pIn.readAllBytes();
	}

	@Override
	public void skipNBytes(long n) throws IOException {
		checkWrite();
		pIn.skipNBytes(n);
	}

	/**
	 * @throws IOException
	 */
	protected void checkErr() throws IOException {}

	protected long checkWrite() throws IOException {
		checkErr();
		if (!wrote) {
			synchronized (this) {
				if (!wrote) {
					long transferred = 0;
					byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
					int read;

					try (OutputStream out = process.getOutputStream()) {
						while ((read = in.read(buffer)) >= 0) {
							out.write(buffer);
							transferred += read;
						}
						out.flush();
					}

					wrote = true;
					return transferred;
				}
			}
		}
		return -1;
	}

	@Override
	public void close() throws IOException {
		pIn.close();
		process.getOutputStream().close();
		process.getInputStream().close();
		process.getErrorStream().close();
		in.close();

		if (process.isAlive()) {
			process.destroy();
			process.onExit().join();
		}
	}
}
