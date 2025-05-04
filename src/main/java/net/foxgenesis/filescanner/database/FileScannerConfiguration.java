package net.foxgenesis.filescanner.database;

import java.util.Objects;

import org.hibernate.validator.constraints.Range;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import net.dv8tion.jda.api.entities.Guild;
import net.foxgenesis.watame.data.PluginConfiguration;

@Entity
public class FileScannerConfiguration extends PluginConfiguration {

	@Column(nullable = false)
	private boolean enabled = false;

	@Column(nullable = false)
	@Range(min = -32, max = 10)
	private short threshold = -2;
	
	@Column(nullable = false)
	@Range(min = 1, max = 100)
	private short strikePercentage = 20;
	
	public FileScannerConfiguration() {}
	public FileScannerConfiguration(Guild guild) {
		setGuild(guild.getIdLong());
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public short getThreshold() {
		return threshold;
	}

	public void setThreshold(short threshold) {
		this.threshold = threshold;
	}

	public short getStrikePercentage() {
		return strikePercentage;
	}

	public void setStrikePercentage(short strikePercentage) {
		this.strikePercentage = strikePercentage;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(enabled, strikePercentage, threshold);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		FileScannerConfiguration other = (FileScannerConfiguration) obj;
		return enabled == other.enabled && strikePercentage == other.strikePercentage && threshold == other.threshold;
	}

	@Override
	public String toString() {
		return "Max0rCustomConfiguration [enabled=" + enabled + ", threshold=" + threshold + ", strikePercentage="
				+ strikePercentage + "]";
	}
}
