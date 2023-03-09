package net.foxgenesis.watame.filescanner.scanner;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.foxgenesis.watame.filescanner.FileScannerPlugin;

public class AttachmentData {
	private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(
			Arrays.asList("jpg", "jpeg", "png", "gif", "webp", "tiff", "svg", "apng"));
	private static final Set<String> VIDEO_EXTENSIONS = new HashSet<>(
			Arrays.asList("webm", "flv", "vob", "avi", "mov", "wmv", "amv", "mp4", "mpg", "mpeg", "gifv"));

	public final Message message;

	private final Attachment attachment;
	private final URL url;

	public AttachmentData(Message message, Attachment attachment) {
		this.message = Objects.requireNonNull(message);
		this.attachment = Objects.requireNonNull(attachment);
		this.url = null;
	}

	public AttachmentData(Message message, URL url) {
		this.message = Objects.requireNonNull(message);
		this.url = Objects.requireNonNull(url);
		this.attachment = null;
	}

	public CompletableFuture<byte[]> getData() {
		return openConnection().thenApplyAsync(in -> {
			try (in) {
				return in.readAllBytes();
			} catch (IOException e) {
				throw new CompletionException(e);
			}
		}, FileScannerPlugin.SCANNING_POOL);
	}

	@SuppressWarnings("resource")
	private CompletableFuture<InputStream> openConnection() {
		if (this.attachment != null)
			return this.attachment.getProxy().download();

		try {
			return CompletableFuture.completedFuture(this.url.openStream());
		} catch (IOException e) {
			return CompletableFuture.failedFuture(e);
		}
	}

	public String getFileName() {
		if (this.attachment != null)
			return this.attachment.getFileName();

		String p = this.url.getFile();

		if (p.isBlank())
			return "";

		int l = p.lastIndexOf('/');
		return l == -1 ? "" : p.substring(l);
	}

	public boolean isVideo() {
		return this.attachment != null ? this.attachment.isVideo() : VIDEO_EXTENSIONS.contains(getFileExtension());
	}

	public boolean isImage() {
		return this.attachment != null ? this.attachment.isImage() : IMAGE_EXTENSIONS.contains(getFileExtension());
	}

	public String getFileExtension() {
		if (this.url != null) {
			String p = this.url.getFile();

			if (p.isBlank())
				return "";

			int l = p.lastIndexOf('.');
			return l == -1 ? "" : p.substring(l);
		}
		return this.attachment.getFileExtension();
	}

	@Override
	public String toString() {
		return "AttachmentData [" + (this.message != null ? "message=" + this.message + ", " : "")
				+ (this.attachment != null ? "attachment=" + this.attachment + ", " : "")
				+ (this.url != null ? "url=" + this.url : "") + "]";
	}
}