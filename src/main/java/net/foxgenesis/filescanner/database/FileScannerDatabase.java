package net.foxgenesis.filescanner.database;

import org.springframework.stereotype.Repository;

import net.foxgenesis.watame.data.PluginRepository;

@Repository
public interface FileScannerDatabase extends PluginRepository<FileScannerConfiguration> {

}
