package net.foxgenesis.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

public class ForkedInputStream extends FilterInputStream {
	private final PipedOutputStream out;
	private final PipedInputStream piped;

	public ForkedInputStream(InputStream in) throws IOException {
		super(in);
		this.out = new PipedOutputStream();
		this.piped = new PipedInputStream(out);
	}
	
	@Override
	public int read() throws IOException {
		int read = super.read();
		out.write(read);
		return read;
	}

	@Override
	public int read(byte b[], int off, int len) throws IOException {
		int read = super.read(b, off, len);
		out.write(b);
		return read;
	}

	@Override
	public long skip(long n) throws IOException {
		return -1;
	}

	@Override
	public synchronized void mark(int readlimit) {}

	@Override
	public synchronized void reset() throws IOException {}

	public InputStream getForkedStream() {
		return piped;
	}

	@Override
	public void close() throws IOException {
		super.close();
		out.close();
	}
}
