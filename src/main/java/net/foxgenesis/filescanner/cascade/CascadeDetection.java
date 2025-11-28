package net.foxgenesis.filescanner.cascade;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.function.Consumer;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.foxgenesis.filescanner.cascade.haar.HaarCascade;
import net.foxgenesis.filescanner.cascade.util.OpenCVUtils;
import net.foxgenesis.watame.util.discord.AttachmentData;

public class CascadeDetection implements Subscriber<CascadeDetectionData> {

	private static final Logger logger = LoggerFactory.getLogger(CascadeDetection.class);

	private final CopyOnWriteArrayList<CascadeEntry> cascades = new CopyOnWriteArrayList<>();
	private final OpenCVProperties properties;
	private Consumer<Exception> errorHandler = null;

	protected Subscription subscription;

	public CascadeDetection(OpenCVProperties properties) {
		this.properties = Objects.requireNonNull(properties);
	}

	@Override
	public final void onSubscribe(Subscription subscription) {
		this.subscription = Objects.requireNonNull(subscription);
		subscription.request(1);
	}

	@Override
	public final void onNext(CascadeDetectionData scannerData) {
		subscription.request(1);

		Mat[] mats = null;
		try {
			attachment: for (AttachmentData attachment : scannerData.getAttachments()) {
				OpenCVUtils.release(mats);

				String attachmentName = attachment.getFileName();
				logger.debug("Getting Mat for {}", attachmentName);

				long start = System.currentTimeMillis();
				mats = getAllRotations(getMatFromAttachment(attachment));

				for (CascadeEntry entry : cascades) {
					if (entry.cascade().findAny(mats, scannerData.getConfig().isStupidMode())) {
						long end = System.currentTimeMillis();
						logger.debug("Time Took [{}]: {}ms", attachmentName, (end - start));

						entry.consumer().accept(scannerData);
						break attachment;
					}
				}

				long end = System.currentTimeMillis();
				logger.debug("Time Took [{}]: {}ms", attachmentName, (end - start));
			}
		} catch (Exception e) {
			if (errorHandler == null)
				throw new CompletionException(e);
			else
				errorHandler.accept(e);
		} finally {
			OpenCVUtils.release(mats);
		}
	}

	@Override
	public void onError(Throwable throwable) {
		logger.error("Error in CascadeDetection", throwable);
		subscription.request(1);
	}

	@Override
	public void onComplete() {
		logger.info("Queue Finished");
	}

	private Mat getMatFromAttachment(AttachmentData attachment) throws IOException {
		Mat image = null;
		try (InputStream in = attachment.openConnection()) {
			MatOfByte mob = new MatOfByte(in.readAllBytes());
			if (mob.empty()) {
				mob.release();
				throw new IOException("MatOfByte was empty from conversion");
			}

			image = Imgcodecs.imdecode(mob, Imgcodecs.IMREAD_COLOR); // Or IMREAD_GRAYSCALE, IMREAD_UNCHANGED
			mob.release(); // Release the MatOfByte

			// Pre-process
			Imgproc.cvtColor(image, image, Imgproc.COLOR_BGR2GRAY);
			Imgproc.medianBlur(image, image, properties.getPre().getBlurSize());
			Imgproc.equalizeHist(image, image);
			return image;
		} catch (Exception e) {
			OpenCVUtils.release(image);
			throw e;
		}
	}

	private Mat[] getAllRotations(Mat mat) {
		double rotation = properties.getPre().getRotation();
		if (rotation <= 0)
			return new Mat[] { mat };

		Mat[] mats = new Mat[(int) (360 / rotation)];
		mats[0] = mat;

		logger.debug("Getting {} rotations", mats.length - 1);
		long start = System.currentTimeMillis();
		try {
			int index = 1;
			for (double rot = rotation; rot < 360; rot += rotation)
				mats[index++] = OpenCVUtils.getRotation(mat, rot);

			return mats;
		} catch (Exception e) {
			OpenCVUtils.release(mats);
			throw e;
		} finally {
			long end = System.currentTimeMillis();
			logger.debug("Rotation time took: " + (end - start) + "ms");
		}
	}

	public boolean addCascade(HaarCascade cascade, Consumer<CascadeDetectionData> consumer) {
		return cascades.add(new CascadeEntry(cascade, consumer));
	}

	public boolean addCascades(CascadeEntry... cascades) {
		return addCascades(List.of(cascades));
	}

	public boolean addCascades(Collection<CascadeEntry> cascades) {
		return this.cascades.addAll(cascades);
	}

	public void setErrorHandler(Consumer<Exception> handler) {
		this.errorHandler = handler;
	}
}
