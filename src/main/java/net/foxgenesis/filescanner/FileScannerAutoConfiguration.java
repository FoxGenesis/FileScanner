package net.foxgenesis.filescanner;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.foxgenesis.filescanner.database.FileScannerConfigurationService;
import net.foxgenesis.springJDA.annotation.GatewayIntents;
import net.foxgenesis.springJDA.annotation.Permissions;
import net.foxgenesis.springJDA.annotation.SpringJDAAutoConfiguration;
import net.foxgenesis.watame.plugin.WatamePlugin;
import net.foxgenesis.watame.util.lang.DiscordLocaleMessageSource;

@EntityScan
@ComponentScan
@EnableJpaRepositories
@SpringJDAAutoConfiguration
@WatamePlugin(id = "filescanner")
@EnableConfigurationProperties(Config.class)
public class FileScannerAutoConfiguration {

	@Bean
	@Permissions({ Permission.MESSAGE_MANAGE, Permission.MESSAGE_EMBED_LINKS })
	@GatewayIntents({ GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT })
	FileScanner fileScannerListener(FileScannerConfigurationService service, Config config,
			DiscordLocaleMessageSource messages) {
		return new FileScanner(service, messages, config);
	}
}
