package net.foxgenesis.filescanner;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.foxgenesis.filescanner.database.FileScannerConfiguration;
import net.foxgenesis.watame.util.discord.AttachmentData;

public abstract class LoudScanner implements Subscriber<ScannerData> {
	/**
	 * length of EBUR128 tag in ffmpeg
	 */
	private static final int EBUR128 = 35;

	protected static final Logger logger = LoggerFactory.getLogger(LoudScanner.class);

	protected Subscription subscription;

	@Override
	public final void onSubscribe(Subscription subscription) {
		this.subscription = Objects.requireNonNull(subscription);
		subscription.request(1);
	}

	@Override
	public final void onNext(ScannerData scannerData) {
		subscription.request(1);

		Message message = scannerData.message();

		FileScannerConfiguration config = scannerData.config();
		short threshold = config.getThreshold();
		double percent = Math.max(0, Math.min(1, config.getStrikePercentage() / 100D));

		for (AttachmentData attachment : scannerData.getAttachments()) {
			String attachmentName = attachment.getFileName();
			logger.debug("Getting EBUR128 for {}", attachmentName);

			// ============ PROCESSING START ============
			long startTime = System.currentTimeMillis();
			// ============ PROCESSING START ============

			// Process the attachment through FFMPEG and parse LU values from EBUR128
			// results
			List<Double> lu = processAttachment(attachment);
			// Get the sum of consecutive values greater than the loudness threshold
			List<Integer> strikeChunks = getStrikeChunks(lu, threshold);
			// Total amount of LU values
			double total = lu.size();
			// Get largest loud segment
			double loudChunkPercent = strikeChunks.stream()
					// Get the quotient of the chunk value and total LU values
					.mapToDouble(strikeChunk -> strikeChunk / total)
					// Get largest quotient
					.max()
					// Or 0
					.orElse(0);
			// Check if loud chunk spans more than X percent of the video
			boolean isLoud = loudChunkPercent >= percent;

			// ============== PROCESSING END ==============
			long end = System.currentTimeMillis();
			// ============== PROCESSING END ==============

			logger.debug("LU [{}]: {}", attachmentName, lu);
			logger.debug("Strike Chunks (LU > {}) [{}]: {}", threshold, attachmentName, strikeChunks);
			logger.debug("Is Loud [{}]: {} >= {} = {}", attachmentName, loudChunkPercent, percent, isLoud);
			logger.debug("EBUR128 for [{}] completed in {} sec(s)", attachment.getFileName(),
					"%,.2f".formatted((end - startTime) / 1_000D));

			// If message had loud video, delete message and display error
			if (isLoud) {
				message.replyEmbeds(scannerData.getLoudVideoEmbed(loudChunkPercent, percent, threshold))
						// Map to message deletion
						.flatMap(
								// If we can talk, create embeds and delete messages in current channel
								m -> message.getChannel().canTalk() && canDoInChannel(message.getGuildChannel(),
										Permission.MESSAGE_MANAGE, Permission.MESSAGE_EMBED_LINKS),
								// Delete message with localized reason
								m -> message
										// Delete
										.delete()
										// Add reason
										.reason(scannerData.messages().getMessage("filescanner.reason",
												scannerData.messages()
														.getLocaleForGuild(scannerData.message().getGuild()))))
						// Send
						.queue(v -> { // On success: log
							logger.info("Removing loud video from {}", message.getAuthor().toString());
						}, error -> { // On error: log
							logger.error("Failed to remove message from " + message.getAuthor().toString(), error);
						});
				break;
			}
		}
	}

	protected abstract List<Double> processAttachment(AttachmentData data);

	/**
	 * Parse the momentary LU (Loudness Unit) values from FFMPEG EBUR128 output.
	 * 
	 * @param stream - stream of FFMPEG output
	 * 
	 * @return Returns a {@link List} of LU values
	 */
	protected List<Double> getLUValues(Stream<String> stream) {
		return stream.filter(s -> s.startsWith("[Parsed_ebur128_0")).map(s -> {
			int start = s.indexOf("M:", EBUR128) + 2;
			if (start < 2)
				return Double.NaN;
			int end = s.indexOf("S:", start);

			String loudStr = s.substring(start, end);

			try {
				return Double.parseDouble(loudStr);
			} catch (NumberFormatException ex) {
				return Double.NaN;
			}
		}).filter(d -> !d.equals(Double.NaN)).toList();
	}

	/**
	 * Convert EBUR 128 segments into chunks of loudness.
	 * 
	 * @param segments - EBUR 128 segments
	 * 
	 * @return Returns a {@link List} of chunks
	 * 
	 * @author Spazmaster
	 */
	protected List<Integer> getStrikeChunks(List<Double> segments, double threshold) {
		List<Integer> strikeChunks = new ArrayList<>();
		int strikes = 0;
		/*
		 * sometimes a video could have a loud peak for less than a second, possibly due
		 * to random noise or encoding error. This acts as a sort of "forgiveness meter"
		 * so that it takes more than a one-time detection of loud audio
		 */
		for (double value : segments)
			if (value > threshold) {
				// if the loudness value is greater than -4.5
				strikes++;
			} else if (strikes > 0) {
				// otherwise, we have gone back to a segment that isnt loud anymore and we can
				// add a group of loud chunks back into a
				// strike cache
				strikeChunks.add(strikes);
				strikes = 0;
			}
		// end for loop

		if (strikes > 0) {
			// if video ended with loud strikes, then add those chunks as well
			strikeChunks.add(strikes);
		}

		return strikeChunks;
	}

	/**
	 * Check if self member has the specified {@link Permission Permissions} in a
	 * {@link GuildMessageChannelUnion}.
	 * 
	 * @param channel     - channel to check
	 * @param permissions - required permissions
	 * 
	 * @return Returns {@code true} if self member has the specified
	 *         {@code permissions} in the {@code channel}
	 */
	private static boolean canDoInChannel(GuildMessageChannelUnion channel, Permission... permissions) {
		return channel.getGuild().getSelfMember().hasPermission(channel, permissions);
	}

	@Override
	public void onError(Throwable throwable) {
		logger.error("Error in EBUR128Subscriber", throwable);
		subscription.request(1);
	}

	@Override
	public void onComplete() {
		logger.info("Queue Finished");
	}
}
