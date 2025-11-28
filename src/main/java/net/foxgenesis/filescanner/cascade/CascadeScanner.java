package net.foxgenesis.filescanner.cascade;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.HttpException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.foxgenesis.filescanner.Config;
import net.foxgenesis.filescanner.database.FileScannerConfiguration;
import net.foxgenesis.filescanner.database.FileScannerConfigurationService;
import net.foxgenesis.watame.util.PrefixedThreadFactory;
import net.foxgenesis.watame.util.PushbulletService;
import net.foxgenesis.watame.util.discord.AttachmentData;
import net.foxgenesis.watame.util.discord.DiscordUtils;
import net.foxgenesis.watame.util.lang.DiscordLocaleMessageSource;

public class CascadeScanner extends ListenerAdapter implements AutoCloseable {

	private static final Predicate<String> meanPattern = Pattern.compile(
			"\\b(?:where is|not quite|wrong|false|untrue|negative|not a|kill yourself|incorrect|no|stupid|fuck (?:you|off)|clanker|how|dumbass|dumb|cunt|retard|are you sure|nuh)\\b",
			Pattern.CASE_INSENSITIVE).asPredicate();

	private final FileScannerConfigurationService service;
	private final DiscordLocaleMessageSource messages;

	private final SubmissionPublisher<CascadeDetectionData> publisher;
	private final ExecutorService executor;

	@Autowired
	private Optional<PushbulletService> pushbullet;

	public CascadeScanner(FileScannerConfigurationService service, DiscordLocaleMessageSource messages, Config config,
			Collection<CascadeEntry> cascadeEntries) {
		this.service = Objects.requireNonNull(service);
		this.messages = Objects.requireNonNull(messages);

		// Setup Subscriber/Publisher
		this.executor = config.isCommonPool() ? ForkJoinPool.commonPool()
				: Executors.newFixedThreadPool(config.getWorkers(), new PrefixedThreadFactory("Cascade Scanning"));
		this.publisher = new SubmissionPublisher<>(executor, config.getBuffer());

		CascadeDetection detection = new CascadeDetection(config.getOpencv());
		detection.addCascades(cascadeEntries);
		detection.setErrorHandler(err -> {
			Logger logger = LoggerFactory.getLogger(detection.getClass());
			if (err instanceof HttpException || err instanceof IOException) {
				logger.warn("***Suppressed*** exception during cascade detection", err);
				return;
			}
			logger.error("Error in cascade detection", err);
			pushbullet
					.ifPresent(pb -> pb.sendPushNote("Error in cascade detection", ExceptionUtils.getStackTrace(err)));
		});
		publisher.subscribe(detection);
	}

	@Override
	public void onMessageReceived(MessageReceivedEvent e) {
		if (!e.isFromGuild())
			return;

		Guild guild = e.getGuild();
		// Do not scan non user messages
		if (e.getAuthor().isBot() || e.getAuthor().isSystem())
			return;

		// Check if we can talk, send embeds and delete messages
		if (!(e.getChannel().canTalk() && guild.getSelfMember().hasPermission(e.getGuildChannel(),
				Permission.MESSAGE_ADD_REACTION, Permission.MESSAGE_EMBED_LINKS)))
			return;

		// Stop is publisher is closed
		if (publisher.isClosed())
			return;

		Message message = e.getMessage();

		// Check if message is reply to detection and permission to timeout
		if (isReplyToDetection(message) && canTimeout(message, e.getGuildChannel())) {
			Message ref = message.getReferencedMessage();

			String detected = getDetected(ref);

			final String format = """
					It's obviously a %s. Stop being stupid.
					-# %s has been timed out for 1 minute
					""";
			message.getMember().timeoutFor(1, TimeUnit.MINUTES).reason("Failed to find " + detected)
					.flatMap(v -> message.reply(format.formatted(detected, message.getMember().getAsMention())))
					.queue();
		}

		service.get(guild)
				// Check if enabled
				.filter(FileScannerConfiguration::isEnabled)
				// If enabled, submit
				.ifPresent(config -> {
					List<AttachmentData> attachments = DiscordUtils.getAttachments(e.getMessage(), true);
					if (attachments.isEmpty())
						return;
					attachments.removeIf(AttachmentData::isVideo);
					attachments.removeIf(data -> !data.isStandardImage());
					if (!attachments.isEmpty())
						publisher.submit(new CascadeDetectionData(e.getMessage(), attachments, config, messages));
				});
	}

	@Override
	public void close() throws Exception {
		Logger logger = LoggerFactory.getLogger(getClass());
		if (!(publisher == null || publisher.isClosed())) {
			logger.info("Closing Cascade publisher");
			publisher.close();
		}
		if (!executor.equals(ForkJoinPool.commonPool())) {
			logger.info("Closing Cascade executor");
			executor.shutdown();
		}
	}

	private boolean isReplyToDetection(Message message) {
		// Check if message is reply
		if (message.getType() != MessageType.INLINE_REPLY)
			return false;

		Message ref = message.getReferencedMessage();
		// Fail if no reference message
		if (ref == null)
			return false;

		// Check if message is reply to bot
		if (!ref.getMember().equals(message.getGuild().getSelfMember()))
			return false;

		return (ref.getContentRaw().startsWith("This is a") || ref.getContentRaw().startsWith("It's obviously a"))
				&& meanPattern.test(message.getContentStripped());
	}

	private String getDetected(Message message) {
		String raw = message.getContentRaw();
		if (raw.startsWith("This is a"))
			return raw.substring("This is a".length() + 1, raw.lastIndexOf('!'));
		else
			return raw.substring("It's obviously a".length() + 1, raw.indexOf('.'));
	}

	private boolean canTimeout(Message message, GuildChannel channel) {
		Member self = message.getGuild().getSelfMember();
		if (!self.hasPermission(channel, Permission.MODERATE_MEMBERS))
			return false;

		return true;
	}
}
