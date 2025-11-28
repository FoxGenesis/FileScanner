package net.foxgenesis.filescanner.cascade.util;

import org.opencv.core.MatOfByte;

public class CloseableMatOfByte extends MatOfByte implements OpenCVCloseable {

	public CloseableMatOfByte(byte[] allBytes) {
		super(allBytes);
	}

	@Override
	public void close() {
		release();
	}
}
