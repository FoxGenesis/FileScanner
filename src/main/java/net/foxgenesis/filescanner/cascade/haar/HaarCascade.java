package net.foxgenesis.filescanner.cascade.haar;

import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;

@FunctionalInterface
public interface HaarCascade {
	default MatOfRect detect(Mat mat) {
		return detect(mat, false);
	}

	MatOfRect detect(Mat mat, boolean stupidMode);

	default boolean find(Mat mat) {
		return find(mat, false);
	}

	default boolean find(Mat mat, boolean stupidMode) {
		MatOfRect rect = detect(mat, stupidMode);
		boolean found = !rect.empty();
		rect.release();
		return found;
	}
	
	default boolean findAny(Mat[] mats) {
		return findAny(mats, false);
	}

	default boolean findAny(Mat[] mats, boolean stupidMode) {
		for (Mat mat : mats)
			if (find(mat, stupidMode))
				return true;
		return false;
	}
}
