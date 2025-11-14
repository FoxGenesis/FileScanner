package net.foxgenesis.filescanner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.Flow;

import org.hibernate.validator.constraints.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;

@Validated
@ConfigurationProperties(prefix = "filescanner")
public class Config implements Validator {
	private final Logger logger = LoggerFactory.getLogger(getClass());

	private boolean commonPool = true;

	@Range(min = 1, max = 10)
	private int workers = 2;

	@Min(1)
	private int buffer = Flow.defaultBufferSize();

	private Path qtfs = Paths.get("lib", getQTLibraryBySystem(System.getProperty("os.name").toLowerCase()));

	private Path ffmpegPath = Path.of("ffmpeg");

	private Path ffprobePath = Path.of("ffprobe");

	private boolean useComponentV2 = true;

	public boolean isCommonPool() {
		return commonPool;
	}

	public void setCommonPool(boolean commonPool) {
		this.commonPool = commonPool;
	}

	public int getWorkers() {
		return workers;
	}

	public void setWorkers(int workers) {
		this.workers = workers;
	}

	public int getBuffer() {
		return buffer;
	}

	public void setBuffer(int buffer) {
		this.buffer = buffer;
	}

	public Path getQtfs() {
		return qtfs;
	}

	public void setQtfs(Path qtTransformer) {
		this.qtfs = qtTransformer;
	}

	public Path getFfmpegPath() {
		return ffmpegPath;
	}

	public void setFfmpegPath(Path ffmpegPath) {
		this.ffmpegPath = ffmpegPath;
	}

	public Path getFfprobePath() {
		return ffprobePath;
	}

	public void setFfprobePath(Path ffprobePath) {
		this.ffprobePath = ffprobePath;
	}

	public boolean isUseComponentV2() {
		return useComponentV2;
	}

	public void setUseComponentV2(boolean useComponentV2) {
		this.useComponentV2 = useComponentV2;
	}

	@Override
	public boolean supports(Class<?> clazz) {
		return Config.class.isAssignableFrom(clazz);
	}

	@Override
	public void validate(Object target, Errors errors) {
		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "qtfs", "field.required");
		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "ffmpegPath", "field.required");
		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "ffprobePath", "field.required");

		Config config = (Config) target;
		try {
			isFFMPEGInstalled(config.getFfmpegPath());
		} catch (Exception e) {
			e.printStackTrace();
			errors.rejectValue("ffmpegPath", "field.version.failed", new Object[] { config.getFfmpegPath() },
					"Unnable to validate FFMPEG at path: [" + config.getFfmpegPath() + "]");
		}
		try {
			isFFProbeInstalled(config.getFfprobePath());
		} catch (Exception e) {
			e.printStackTrace();
			errors.rejectValue("ffprobePath", "field.version.failed", new Object[] { config.getFfprobePath() },
					"Unnable to validate FFProbe at path: [" + config.getFfprobePath() + "]");
		}
		try {
			isQTLibraryValid(config.qtfs);
		} catch (Exception e) {
			e.printStackTrace();
			errors.rejectValue("qtTransformer", "field.version.failed", new Object[] { config.qtfs },
					"Unnable to validate QuickTime-FastStart at path: [" + config.qtfs + "]");
		}
	}

	private boolean isFFMPEGInstalled(Path path) throws Exception {
		Process p = null;
		try {
			p = new ProcessBuilder(path.toString(), "-version").start();
			try (InputStream in = p.getInputStream()) {
				logger.info("FFMPEG version: " + new String(in.readAllBytes()).split("\n", 2)[0].trim());
			}
			return true;
		} finally {
			if (p != null)
				p.destroy();
		}
	}

	private boolean isFFProbeInstalled(Path path) throws Exception {
		Process p = null;
		try {
			p = new ProcessBuilder(path.toString(), "-version").start();
			try (InputStream in = p.getInputStream()) {
				logger.info("FFProbe version: " + new String(in.readAllBytes()).split("\n", 2)[0].trim());
			}
			return true;
		} finally {
			if (p != null)
				p.destroy();
		}
	}

	private void isQTLibraryValid(Path path) throws IOException {
		Process p = null;
		try {
			p = new ProcessBuilder(path.toString(), "-v").start();
			try (InputStream in = p.getInputStream()) {
				String version = new String(in.readAllBytes()).split("\n", 2)[0].trim();

				if (version == null || version.isBlank())
					throw new IOException("Unable to get version");

				logger.info("QuickTime-FastStart library version: {}", version);
			}
		} finally {
			if (p != null)
				p.destroy();
		}
	}

	@Override
	public int hashCode() {
		return Objects.hash(buffer, commonPool, ffmpegPath, ffprobePath, qtfs, useComponentV2, workers);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Config other = (Config) obj;
		return buffer == other.buffer && commonPool == other.commonPool && Objects.equals(ffmpegPath, other.ffmpegPath)
				&& Objects.equals(ffprobePath, other.ffprobePath) && Objects.equals(qtfs, other.qtfs)
				&& useComponentV2 == other.useComponentV2 && workers == other.workers;
	}

	@Override
	public String toString() {
		return "Config [commonPool=" + commonPool + ", workers=" + workers + ", buffer=" + buffer + ", qtfs=" + qtfs
				+ ", ffmpegPath=" + ffmpegPath + ", ffprobePath=" + ffprobePath + ", useComponentV2=" + useComponentV2
				+ "]";
	}

	private static String getQTLibraryBySystem(String system) {
		if (system.startsWith("linux"))
			return "qtfs";
		if (system.startsWith("windows"))
			return "qt-faststart-x86.exe";

		throw new UnsupportedOperationException("[QuickTime-FastStart] Unsupported Operating System: " + system);
	}
}
