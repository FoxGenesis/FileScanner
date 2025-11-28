package net.foxgenesis.filescanner.cascade.util;

import org.opencv.core.Mat;

public class CloseableMat extends Mat implements OpenCVCloseable  {

	public CloseableMat() {
		super();
	}

	public CloseableMat(Mat mat) {
		super(mat.nativeObj);
	}

	@Override
	public void close() {
		release();
	}
}
