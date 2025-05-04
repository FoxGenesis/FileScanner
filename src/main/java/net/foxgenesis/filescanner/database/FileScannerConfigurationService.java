package net.foxgenesis.filescanner.database;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import net.dv8tion.jda.api.entities.Guild;

@Service
@CacheConfig(cacheNames={"filescanner"})
public class FileScannerConfigurationService {

	@Autowired
	private FileScannerDatabase database;
	
	@Cacheable(key = "#guild.idLong")
	public Optional<FileScannerConfiguration> get(Guild guild) {
		return database.findByGuild(guild);
	}
	
	@CachePut(key = "#guild.idLong")
	public Optional<FileScannerConfiguration> getFresh(Guild guild) {
		return database.findByGuild(guild);
	}
	
	@CacheEvict(key = "#guild.idLong")
	public void delete(Guild guild) {
		database.deleteByGuild(guild);
	}
	
	@CachePut(key = "#config.guild")
	public FileScannerConfiguration save(FileScannerConfiguration config) {
		return database.save(config);
	}
}
