package net.foxgenesis.filescanner.database;

import java.util.LinkedHashSet;
import java.util.Set;

import org.hibernate.validator.constraints.Range;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.foxgenesis.watame.data.PluginConfiguration;

@Entity
@Getter
@Setter
public class FileScannerConfiguration extends PluginConfiguration {

	@Column(nullable = false)
	private boolean enabled = false;

	@Column(nullable = false)
	@Range(min = -32, max = 10)
	private short threshold = -2;

	@Column(nullable = false)
	@Range(min = 1, max = 100)
	private short strikePercentage = 20;

	@ElementCollection(targetClass = Long.class, fetch = FetchType.EAGER)
	@CollectionTable(name = "excluded_channels", joinColumns = @JoinColumn(name = "guild"))
	@Column(nullable = false)
	private Set<Long> excluded = new LinkedHashSet<>();
	
	@Column(nullable = false)
	private boolean stupidMode = false;

	public FileScannerConfiguration() {
	}

	public FileScannerConfiguration(Guild guild) {
		setGuild(guild.getIdLong());
	}
	
	public boolean isExcluded(Channel channel) {
		return excluded.contains(channel.getIdLong());
	}
}
