package net.foxgenesis.watame.filescanner.scanner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import net.dv8tion.jda.api.entities.Message.Attachment;

public class AttachmentManager {

	private final List<AttachmentScanner> scanners = new ArrayList<>();

	public boolean addScanner(AttachmentScanner scanner) {
		return scanner == null ? false : scanners.contains(scanner) ? false : scanners.add(scanner);
	}

	public CompletableFuture<Void> testAttachment(byte[] in, Attachment attachment) {
		return CompletableFuture.anyOf(scanners.stream().map(scanner -> scanner.testAttachment(in, attachment)).toList()
				.toArray(new CompletableFuture[scanners.size()])).thenApplyAsync(obj -> null);
	}
}
