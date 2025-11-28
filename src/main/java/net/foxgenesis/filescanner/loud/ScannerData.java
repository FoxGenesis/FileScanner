package net.foxgenesis.filescanner.loud;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.foxgenesis.filescanner.database.FileScannerConfiguration;
import net.foxgenesis.watame.util.StringUtils;
import net.foxgenesis.watame.util.discord.AttachmentData;
import net.foxgenesis.watame.util.discord.Colors;
import net.foxgenesis.watame.util.lang.DiscordLocaleMessageSource;
import net.foxgenesis.watame.util.lang.Localized;
import net.foxgenesis.watame.util.lang.LocalizedContainerBuilder;
import net.foxgenesis.watame.util.lang.LocalizedEmbedBuilder;
import net.foxgenesis.watame.util.lang.LocalizedSectionBuilder;

public record ScannerData(Message message, FileScannerConfiguration config, DiscordLocaleMessageSource messages) {

	/**
	 * Get all attachments from a {@link Message}.
	 * 
	 * @param message - message to check
	 * 
	 * @return Returns a {@link List} of {@link AttachmentData}
	 */
	public List<AttachmentData> getAttachments() {
		List<AttachmentData> attachments = new ArrayList<>();
		message.getAttachments().forEach(a -> attachments.add(new AttachmentData(message, a)));
		StringUtils.findURLs(message.getContentRaw()).forEach(u -> attachments.add(new AttachmentData(message, u)));
		attachments.removeIf(a -> !a.isVideo());
		return attachments;
	}
	
	/**
	 * Construct a {@link MessageEmbed} declaring that a video was loud.
	 * @param threshold 
	 * 
	 * @param message - {@link Message} to construct embed with
	 * 
	 * @return Returns a {@link MessageEmbed} declaring that a video was loud
	 */
	public MessageEmbed getLoudVideoEmbed(double loudness, double max, short threshold) {
		LocalizedEmbedBuilder builder = new LocalizedEmbedBuilder(messages, messages.getLocaleForGuild(message.getGuild()));
		builder.setColor(Colors.ERROR);
		builder.setThumbnail("https://www.kindpng.com/picc/m/275-2754352_sony-mdrv6-anime-hd-png-download.png");

		builder.setLocalizedTitle("filescanner.embed.title");
		builder.appendLocalizedDescription("filescanner.embed.description",
				message.getAuthor().getAsMention());

		builder.setLocalizedFooter("filescanner.embed.footer", loudness, max, threshold);
		
		return builder.build();
	}
	
	public Container getLoudVideoContainer(double loudness, double max, short threshold) {
		LocalizedContainerBuilder builder = new LocalizedContainerBuilder(messages, messages.getLocaleForGuild(message.getGuild()));
		builder.setColor(Colors.ERROR);
		
		LocalizedSectionBuilder sb = builder.getNewLocalizedSectionBuilder();
		sb.setThumbnailUrl("https://www.kindpng.com/picc/m/275-2754352_sony-mdrv6-anime-hd-png-download.png");
		sb.addLocalizedFormattedTextDisplay("## %s", Localized.resolved("filescanner.embed.title"));
		sb.addLocalizedTextDisplay("filescanner.embed.description", message.getAuthor().getAsMention());
		sb.addLocalizedFormattedTextDisplay("-# %s", Localized.resolved("filescanner.embed.footer", loudness, max, threshold));
		
		builder.addSectionAndClear(sb);
		
		return builder.build();
	}
}
