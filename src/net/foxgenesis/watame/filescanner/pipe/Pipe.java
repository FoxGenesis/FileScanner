package net.foxgenesis.watame.filescanner.pipe;

import java.io.IOException;
import java.io.PipedInputStream;
import java.util.concurrent.CompletableFuture;

public interface Pipe extends AutoCloseable {

	void pipeData() throws IOException;
	
	PipedInputStream getConnection() throws IOException;

	CompletableFuture<Void> onExit();
}
