package net.foxgenesis.watame.filescanner.scanner;

import java.util.concurrent.CompletableFuture;

import net.dv8tion.jda.api.entities.Message.Attachment;

public class LoudVideoDetection implements AttachmentScanner {

	@Override
	public CompletableFuture<Void> testAttachment(byte[] in, Attachment attachment) {

		return null;
	}

}
