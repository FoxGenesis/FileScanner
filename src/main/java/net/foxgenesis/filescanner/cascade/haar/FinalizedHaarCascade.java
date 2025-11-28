package net.foxgenesis.filescanner.cascade.haar;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Function;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;

public class FinalizedHaarCascade extends BasicHaarCascade {

	private final Function<Mat, Size> minimumSize, maximumSize;
	private final Function<Mat, Double> scaleFactor;
	private final Function<Mat, Integer> minimumNeighbors;
	
	public FinalizedHaarCascade(Path resource, @Nullable Function<Mat, Double> scaleFactor,
			@Nullable Function<Mat, Integer> minimumNeighbors, @Nullable Function<Mat, Size> minimumSize,
			@Nullable Function<Mat, Size> maximumSize) throws IOException {
		super(Objects.requireNonNull(resource));
		this.scaleFactor = scaleFactor == null ? mat -> 1.1 : scaleFactor;
		this.minimumNeighbors = minimumNeighbors == null ? mat -> 10 : minimumNeighbors;
		this.minimumSize = minimumSize == null ? mat -> {
			int size = Math.round(mat.rows() * 0.1f);
			return new Size(size, size);
		} : minimumSize;
		this.maximumSize = maximumSize == null ? mat -> new Size() : maximumSize;
	}

	public FinalizedHaarCascade(Resource resource, @Nullable Function<Mat, Double> scaleFactor,
			@Nullable Function<Mat, Integer> minimumNeighbors, @Nullable Function<Mat, Size> minimumSize,
			@Nullable Function<Mat, Size> maximumSize) throws IOException {
		super(Objects.requireNonNull(resource));
		this.scaleFactor = scaleFactor == null ? mat -> 1.1 : scaleFactor;
		this.minimumNeighbors = minimumNeighbors == null ? mat -> 10 : minimumNeighbors;
		this.minimumSize = minimumSize == null ? mat -> {
			int size = Math.round(mat.rows() * 0.1f);
			return new Size(size, size);
		} : minimumSize;
		this.maximumSize = maximumSize == null ? mat -> new Size() : maximumSize;
	}

	protected Size getMinimumFaceSize(Mat loadedImage) {
		return minimumSize.apply(loadedImage);
	}

	protected Size getMaximumFaceSize(Mat loadedImage) {
		return maximumSize.apply(loadedImage);
	}

	protected double getScaleFactor(Mat loadedImage) {
		return scaleFactor.apply(loadedImage);
	}

	protected int getMinimumNeighbors(Mat loadedImage) {
		return minimumNeighbors.apply(loadedImage);
	}
}
