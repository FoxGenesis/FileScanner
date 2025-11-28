package net.foxgenesis.filescanner.cascade.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.function.Consumer;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import net.foxgenesis.watame.util.discord.AttachmentData;

public final class OpenCVUtils {
	public static CloseableMat getMatFromAttachment(AttachmentData attachment) throws IOException {
		Objects.requireNonNull(attachment);

		try (InputStream in = attachment.openConnection()) {
			return OpenCVUtils.getMatFromInputStream(in);
		}
	}

	public static CloseableMat getMatFromInputStream(InputStream in) throws IOException {
		try (CloseableMatOfByte mob = new CloseableMatOfByte(in.readAllBytes())) {
			if (mob.empty())
				throw new IOException("MatOfByte was empty from conversion");
			return new CloseableMat(Imgcodecs.imdecode(mob, Imgcodecs.IMREAD_COLOR)); // Or IMREAD_GRAYSCALE,
																						// IMREAD_UNCHANGED
		}
	}

	public static CloseableMat getRotation(Mat mat, double angle) {
		Objects.requireNonNull(mat);

		if (angle == 0)
			throw new IllegalArgumentException("Mat is already rotated to 0");

		if (angle % 90 != 0)
			return getArbitraryRotation(mat, angle);

		int rotationCode = switch ((int) angle) {
		case 90 -> Core.ROTATE_90_CLOCKWISE;
		case 180 -> Core.ROTATE_180;
		case 270 -> Core.ROTATE_90_COUNTERCLOCKWISE;
		default -> throw new IllegalArgumentException("Unexpected value: " + (int) angle);
		};

		return getSimpleRotation(mat, rotationCode);
	}

	public static CloseableMat getSimpleRotation(Mat mat, int code) {
		Objects.requireNonNull(mat);

		return attemptMatOperation(out -> Core.rotate(mat, out, code));

//		Mat out = new Mat();
//		try {
//			Core.rotate(mat, out, code);
//			return out;
//		} catch (Exception e) {
//			release(out);
//			throw e;
//		}
	}

	public static CloseableMat getArbitraryRotation(Mat mat, double angle) {
		Objects.requireNonNull(mat);

		if (angle == 0)
			throw new IllegalArgumentException("Mat is already rotated to 0");

		Size size = new Size(mat.cols(), mat.rows());
		Point center = new Point(mat.cols() / 2, mat.rows() / 2);
		final double scale = 1.0;

		try (CloseableMat rotMat = new CloseableMat(Imgproc.getRotationMatrix2D(center, angle, scale))) {
			return attemptMatOperation(out -> Imgproc.warpAffine(mat, out, rotMat, size));
		}

//		Mat out = new Mat();
//		Mat rotMat = null;
//		try {
//			rotMat = Imgproc.getRotationMatrix2D(center, angle, scale);
//			Imgproc.warpAffine(mat, out, rotMat, size);
//			return out;
//		} catch (Exception e) {
//			release(out);
//			throw e;
//		} finally {
//			release(rotMat);
//		}
	}

	public static void release(Mat... mats) {
		if (mats == null)
			return;

		for (int i = 0; i < mats.length; i++) {
			if (mats[i] == null)
				continue;

			if (mats[i] instanceof OpenCVCloseable c)
				c.close();
			else
				mats[i].release();
		}
	}

	private static CloseableMat attemptMatOperation(Consumer<Mat> attempt) {
		CloseableMat out = new CloseableMat();
		try {
			attempt.accept(out);
			return out;
		} catch (Exception e) {
			out.close();
			throw e;
		}
	}
}
