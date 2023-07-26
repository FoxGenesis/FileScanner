package net.foxgenesis.watame.filescanner.tester;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.foxgenesis.executor.PrefixedThreadFactory;
import net.foxgenesis.util.StringUtils;
import net.foxgenesis.watame.util.Colors;

public class EBUR128Subscriber implements Subscriber<Message>, Closeable {
	private static final Logger logger = LoggerFactory.getLogger("EBUR128");

	/**
	 * length of EBUR128 tag in ffmpeg
	 */
	private static final int EBUR128 = 35;

	private final Path quickTimeBinaryPath;

	/**
	 * Threshold for strike chunks
	 */
	private final double loudnessThreshold;

	/**
	 * Loudness threshold for loud videos
	 */
	private final double loudnessPercent;

	private final ExecutorService singleExecutor = Executors.newCachedThreadPool(new PrefixedThreadFactory("Video Reader"));

	private Subscription subscription;

	public EBUR128Subscriber(Path quickTimeBinaryPath) {
		this(quickTimeBinaryPath, -2, 0.2);
	}

	public EBUR128Subscriber(Path quickTimeBinaryPath, double loudnessThreshold, double loudnessPercent) {
		this.quickTimeBinaryPath = Objects.requireNonNull(quickTimeBinaryPath);

		// Clamp 0 < loudnessPercent < 1
		this.loudnessPercent = Math.max(0, Math.min(1, loudnessPercent));

		// Clamp 0 > loudnessThreshold > -99
		this.loudnessThreshold = -Math.abs(loudnessThreshold);
	}

	@Override
	public void onSubscribe(Subscription subscription) {
		this.subscription = Objects.requireNonNull(subscription);
		subscription.request(1);
	}

	@Override
	public void onNext(Message message) {
		subscription.request(1);
		attachments: for (AttachmentData data : getAttachments(message)) {
			try {
				logger.debug("Getting EBUR128 for {}", data.getFileName());
				boolean isLoud = false;
				long startTime = System.currentTimeMillis();

				List<Process> pipes = ProcessBuilder.startPipeline(Arrays.asList(
						new ProcessBuilder(this.quickTimeBinaryPath.toString(), "-q").redirectOutput(Redirect.PIPE),
						new ProcessBuilder("ffmpeg", "-hide_banner", "-nostats", "-i", "-", "-af", "ebur128", "-f",
								"null", "-").redirectInput(Redirect.PIPE)));

				try (InputStream in = data.openConnection();
						BufferedReader pErr = pipes.get(pipes.size() - 1).errorReader()) {

					CompletableFuture<Void> write = CompletableFuture.runAsync(() -> {
						try (in; OutputStream out = pipes.get(0).getOutputStream()) {
							in.transferTo(out);
						} catch (IOException e) {
							pipes.forEach(p -> p.destroyForcibly().onExit().join());
							throw new CompletionException(e);
						}
					}, singleExecutor);

					isLoud = checkChunkLoudness(
							getStrikeChunks(pErr.lines().filter(s -> s.startsWith("[Parsed_ebur128_0")).map(s -> {
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
							}).filter(d -> d != Double.NaN).toList()));

					write.join();
				} catch (IOException e) {
					logger.error("Error while getting EBUR128 for [" + data.getFileName() + "]", e);
					pipes.forEach(p -> p.destroyForcibly().onExit().join());
				} finally {
					long end = System.currentTimeMillis();
					pipes.forEach(p -> {
						if (p.isAlive()) {
							p.destroy();
							p.onExit().join();
						}
					});
					logger.debug("EBUR128 for [{}] completed in {} sec(s)", data.getFileName(),
							"%,.2f".formatted((end - startTime) / 1_000D));
				}

				// If message had loud video, delete message and display error
				if (isLoud) {
					message.replyEmbeds(getLoudVideoEmbed(message))
							.flatMap(
									m -> message.getChannel().canTalk() && canDoInChannel(message.getGuildChannel(),
											Permission.MESSAGE_MANAGE, Permission.MESSAGE_EMBED_LINKS),
									m -> message.delete().reason("Loud video"))
							.queue(v -> logger.info("Removing loud video from {}", message.getAuthor().toString()),
									error -> logger.error(
											"Failed to remove message from " + message.getAuthor().toString(), error));
					break attachments;
				}
			} catch (Exception e) {
				logger.error("Error while getting EBUR128 for [" + data.getFileName() + "]", e);
			}
		}
	}

	/**
	 * Convert EBUR 128 segments into chunks of loudness.
	 * 
	 * @param segments - EBUR 128 segments
	 * 
	 * @return Returns a {@link List} of chunks
	 */
	private List<Integer> getStrikeChunks(List<Double> segments) {
		List<Integer> strikeChunks = new ArrayList<>();
		int strikes = 0;
		/*
		 * sometimes a video could have a loud peak for less than a second, possibly due
		 * to random noise or encoding error. This acts as a sort of "forgiveness meter"
		 * so that it takes more than a one-time detection of loud audio
		 */
		for (double value : segments)
			if (value > this.loudnessThreshold) {
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
	 * Check if the time period of the loudness is bigger than 1/5th of the total
	 * video.
	 * 
	 * @param strikeChunks - EBUR 128 loud chunks
	 * 
	 * @return Returns {@code true} if the strike chunks are considered loud
	 */
	private boolean checkChunkLoudness(List<Integer> strikeChunks) {
		double total = strikeChunks.size();

		return strikeChunks.stream().mapToDouble(strikeChunk -> strikeChunk / total)
				.anyMatch(frac -> frac >= this.loudnessPercent);
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

	@Override
	public void close() {
		singleExecutor.shutdown();
	}

	/**
	 * Get all attachments from a {@link Message}.
	 * 
	 * @param message - message to check
	 * 
	 * @return Returns a {@link List} of {@link AttachmentData}
	 */
	private static List<AttachmentData> getAttachments(Message message) {
		List<AttachmentData> attachments = new ArrayList<>();
		message.getAttachments().forEach(a -> attachments.add(new AttachmentData(message, a)));
		StringUtils.findURLs(message.getContentRaw()).forEach(u -> attachments.add(new AttachmentData(message, u)));
		attachments.removeIf(a -> !a.isVideo());
		return attachments;
	}

	/**
	 * Construct a {@link MessageEmbed} declaring that a video was loud.
	 * 
	 * @param message - {@link Message} to construct embed with
	 * 
	 * @return Returns a {@link MessageEmbed} declaring that a video was loud
	 */
	private static MessageEmbed getLoudVideoEmbed(Message message) {
		return new EmbedBuilder().setColor(Colors.ERROR).setTitle("Loud Video Detected").setDescription(message
				.getAuthor().getAsMention() + " Please do not post loud videos without first stating that the video is "
				+ "loud in the message. If you are going to post a loud video, describe in the same message that it is loud.")
				.setThumbnail("https://www.kindpng.com/picc/m/275-2754352_sony-mdrv6-anime-hd-png-download.png")
				.build();
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
}