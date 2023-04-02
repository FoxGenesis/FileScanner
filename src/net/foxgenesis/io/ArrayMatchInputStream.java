package net.foxgenesis.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class ArrayMatchInputStream extends FilterInputStream {
	private final int[] arr;
	private final int length;

	public ArrayMatchInputStream(InputStream in, int[] arr) {
		super(in);
		this.arr = Objects.requireNonNull(arr);
		this.length = arr.length;
		if (this.length == 0)
			throw new IllegalArgumentException("Unable to operate on empty array!");
	}

	public boolean containsArray() throws IOException {
		int hits = 0;
		int data = -1;
		while ((data = read()) != -1) {
			hits = data == arr[hits] ? hits + 1 : 0;
			if (hits == length)
				return true;
		}
		return false;
	}
}
