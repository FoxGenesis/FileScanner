package net.foxgenesis.filescanner.cascade.util;

public interface OpenCVCloseable extends AutoCloseable {

	@Override
	void close();
}
