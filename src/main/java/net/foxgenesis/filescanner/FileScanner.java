package net.foxgenesis.filescanner;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.SubmissionPublisher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.foxgenesis.filescanner.database.FileScannerConfiguration;
import net.foxgenesis.filescanner.database.FileScannerConfigurationService;
import net.foxgenesis.watame.util.PrefixedThreadFactory;
import net.foxgenesis.watame.util.lang.DiscordLocaleMessageSource;

public class FileScanner extends ListenerAdapter implements AutoCloseable {

	private final Pattern LOUD_MESSAGE_PATTERN = Pattern.compile("\\b(loud|ear rape)\\b", Pattern.CASE_INSENSITIVE);

	private final FileScannerConfigurationService service;
	private final DiscordLocaleMessageSource messages;

	private final SubmissionPublisher<ScannerData> publisher;
	private final ExecutorService executor;

	public FileScanner(FileScannerConfigurationService service, DiscordLocaleMessageSource messages, Config config) {
		this.service = Objects.requireNonNull(service);
		this.messages = Objects.requireNonNull(messages);

		// Setup Subscriber/Publisher
		this.executor = config.isCommonPool() ? ForkJoinPool.commonPool()
				: Executors.newFixedThreadPool(config.getWorkers(), new PrefixedThreadFactory("Video Scanning"));
		this.publisher = new SubmissionPublisher<>(executor, config.getBuffer());
		publisher.subscribe(new QTFSSubscriber(config.getFfmpegPath(), config.getQtfs()));
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
				Permission.MESSAGE_MANAGE, Permission.MESSAGE_EMBED_LINKS)))
			return;

		Message message = e.getMessage();
		// Check if the message is declared as loud
		if (LOUD_MESSAGE_PATTERN.asPredicate().test(message.getContentRaw().replaceAll("\\|\\|.*?\\|\\|", "")))
			return;

		service.get(guild).filter(FileScannerConfiguration::isEnabled).ifPresent(config -> {
			if (!publisher.isClosed())
				publisher.submit(new ScannerData(message, config, messages));
		});
	}

	@Override
	public void close() throws Exception {
		Logger logger = LoggerFactory.getLogger(getClass());
		if(!(publisher == null || publisher.isClosed())) {
			logger.info("Closing LoudVideo publisher");
			publisher.close();
		}
		if(!executor.equals(ForkJoinPool.commonPool())) {
			logger.info("Closing LoudVideo executor");
			executor.shutdown();
		}
	}
}
