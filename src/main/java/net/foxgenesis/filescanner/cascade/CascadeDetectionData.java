package net.foxgenesis.filescanner.cascade;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import lombok.Getter;
import net.dv8tion.jda.api.entities.Message;
import net.foxgenesis.filescanner.database.FileScannerConfiguration;
import net.foxgenesis.watame.util.discord.AttachmentData;
import net.foxgenesis.watame.util.lang.DiscordLocaleMessageSource;

@Getter
public class CascadeDetectionData {

	private final Message message;
	private final FileScannerConfiguration config;
	private final DiscordLocaleMessageSource messages;

	private final List<AttachmentData> attachments;

	public CascadeDetectionData(Message message, List<AttachmentData> attachments, FileScannerConfiguration config, DiscordLocaleMessageSource messages) {
		this.message = Objects.requireNonNull(message);
		this.config = Objects.requireNonNull(config);
		this.messages = Objects.requireNonNull(messages);
		this.attachments = Collections.unmodifiableList(attachments);
	}
}
