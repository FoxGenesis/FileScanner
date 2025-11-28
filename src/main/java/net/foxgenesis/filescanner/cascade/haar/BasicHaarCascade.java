package net.foxgenesis.filescanner.cascade.haar;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Size;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.Objdetect;
import org.springframework.core.io.Resource;

public abstract class BasicHaarCascade implements HaarCascade {
	private final CascadeClassifier cascade;

	public BasicHaarCascade(Resource resource) throws IOException {
		if (!resource.exists())
			throw new FileNotFoundException("Failed to find cascade: " + resource);
		if (!resource.isFile())
			throw new IOException("Resource is not a file: " + resource);
		if (!resource.isReadable())
			throw new IOException("File is not readable: " + resource);
		cascade = new CascadeClassifier(resource.getFile().toString());
	}

	public BasicHaarCascade(Path path) throws IOException {
		if (!Files.exists(path))
			throw new FileNotFoundException("Failed to find cascade: " + path);
		if (!Files.isRegularFile(path))
			throw new IOException("Resource is not a file: " + path);
		if (!Files.isReadable(path))
			throw new IOException("File is not readable: " + path);
		cascade = new CascadeClassifier(path.toAbsolutePath().toString());
	}

	public BasicHaarCascade(String location) {
		cascade = new CascadeClassifier(location);
	}

	public boolean isLoaded() {
		return cascade.empty();
	}

	@Override
	public MatOfRect detect(Mat loadedImage, boolean stupidMode) {
		double scaleFactor = stupidMode ? 1.1 : getScaleFactor(loadedImage);
		int minumumNeighbors = stupidMode ? 1 : getMinimumNeighbors(loadedImage);
		final Size minimumSize = Objects.requireNonNull(getMinimumFaceSize(loadedImage));
		final Size maximumSize = Objects.requireNonNull(getMaximumFaceSize(loadedImage));

		MatOfRect facesDetected = new MatOfRect();
		try {
			// Lock object to thread for cascade internal state
			synchronized (cascade) {
				cascade.detectMultiScale(loadedImage, facesDetected, scaleFactor, minumumNeighbors,
						Objdetect.CASCADE_SCALE_IMAGE, minimumSize, maximumSize);
//				cascade.detectMultiScale(loadedImage, facesDetected);
			}
		} catch (Exception e) {
			// Release MatOfRect if exception
			facesDetected.release();
			throw e;
		}
		return facesDetected;
	}

	protected Size getMinimumFaceSize(Mat loadedImage) {
		int size = Math.round(loadedImage.rows() * 0.1f);
		return new Size(size, size);
	}

	protected Size getMaximumFaceSize(Mat loadedImage) {
		return new Size();
	}

	protected abstract double getScaleFactor(Mat loadedImage);

	protected abstract int getMinimumNeighbors(Mat loadedImage);
}
