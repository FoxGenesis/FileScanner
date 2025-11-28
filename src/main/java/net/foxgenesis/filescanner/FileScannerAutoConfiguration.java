package net.foxgenesis.filescanner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.lang.Nullable;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.foxgenesis.filescanner.cascade.CascadeEntry;
import net.foxgenesis.filescanner.cascade.CascadeScanner;
import net.foxgenesis.filescanner.cascade.haar.FinalizedHaarCascade;
import net.foxgenesis.filescanner.cascade.haar.HaarCascade;
import net.foxgenesis.filescanner.database.FileScannerConfigurationService;
import net.foxgenesis.filescanner.loud.FileScanner;
import net.foxgenesis.springJDA.annotation.GatewayIntents;
import net.foxgenesis.springJDA.annotation.Permissions;
import net.foxgenesis.springJDA.annotation.SpringJDAAutoConfiguration;
import net.foxgenesis.watame.plugin.WatamePlugin;
import net.foxgenesis.watame.util.lang.DiscordLocaleMessageSource;
import nu.pattern.OpenCV;

@EntityScan
@ComponentScan
@EnableJpaRepositories
@SpringJDAAutoConfiguration
@WatamePlugin(id = "filescanner")
@EnableConfigurationProperties(Config.class)
public class FileScannerAutoConfiguration {
	private static final Logger logger = LoggerFactory.getLogger(FileScannerAutoConfiguration.class);

	public static final String HAAR_CASCADE = "filescanner.enable-haarcascades";

	@Bean
	@Permissions({ Permission.MESSAGE_MANAGE, Permission.MESSAGE_EMBED_LINKS })
	@GatewayIntents({ GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT })
	FileScanner fileScannerListener(FileScannerConfigurationService service, Config config,
			DiscordLocaleMessageSource messages) {
		return new FileScanner(service, messages, config);
	}

	@Configuration
	@ConditionalOnProperty(value = HAAR_CASCADE, matchIfMissing = true)
	public static class HaarCascades {
		static {
			logger.info("Attempting to load OpenCV natives");
			OpenCV.loadLocally();
		}

		@Bean
		@Permissions({ Permission.MESSAGE_ADD_REACTION, Permission.MESSAGE_EMBED_LINKS, Permission.MODERATE_MEMBERS })
		@GatewayIntents({ GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT })
		CascadeScanner catScanner(FileScannerConfigurationService service, Config config,
				DiscordLocaleMessageSource messages, ResourceLoader loader) throws IOException {
			List<CascadeEntry> cascades = new ArrayList<>();
			cascades.add(getCatCascade(loader));
			return new CascadeScanner(service, messages, config, cascades);
		}

		private final CascadeEntry getCatCascade(ResourceLoader loader) throws IOException {
			HaarCascade cascade = getCascade(loader,
					ResourceLoader.CLASSPATH_URL_PREFIX + "/haar/haarcascade_frontalcatface_extended.xml", mat -> 1.1,
					mat -> 4, null, null);
			return new CascadeEntry(cascade, scannerData -> {
				if (!scannerData.getConfig().isExcluded(scannerData.getMessage().getChannel()))
					scannerData.getMessage().reply("This is a cat!").queue();

				scannerData.getMessage().addReaction(Emoji.fromCustom("happeh", 478378484025131010L, false))
						.queue(v -> {
						}, new ErrorHandler().ignore(ErrorResponse.REACTION_BLOCKED));
			});
		}

		private static HaarCascade getCascade(ResourceLoader loader, String location,
				@Nullable Function<Mat, Double> scaleFactor, @Nullable Function<Mat, Integer> minimumNeighbors,
				@Nullable Function<Mat, Size> minimumSize, @Nullable Function<Mat, Size> maximumSize)
				throws IOException {
			Resource resource = loader.getResource(location);
			try {
				return new FinalizedHaarCascade(resource, scaleFactor, minimumNeighbors, minimumSize, maximumSize);
			} catch (IOException e) {
				return getTempResource(resource, scaleFactor, minimumNeighbors, minimumSize, maximumSize);
			}
		}

		private static HaarCascade getTempResource(Resource location, @Nullable Function<Mat, Double> scaleFactor,
				@Nullable Function<Mat, Integer> minimumNeighbors, @Nullable Function<Mat, Size> minimumSize,
				@Nullable Function<Mat, Size> maximumSize) throws IOException {
			try (InputStream in = location.getInputStream()) {
				Path tempFile = Files.createTempFile(null, ".xml");
				logger.warn("Failed to open {}. Attempting to open via temporary file: {}", location, tempFile);
				tempFile.toFile().deleteOnExit();
				Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
				return new FinalizedHaarCascade(tempFile, scaleFactor, minimumNeighbors, minimumSize, maximumSize);
			}
		}
	}
}
