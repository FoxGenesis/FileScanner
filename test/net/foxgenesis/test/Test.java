package net.foxgenesis.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import net.foxgenesis.io.AsyncProcessInputStream;
import net.foxgenesis.io.ForkedInputStream;

public class Test {
	private static final int EBUR128 = 38;

	public static void main(String[] args) throws Exception {
		Path quickTimeBinaryPath = Path.of("lib", "qt-faststart-x86.exe");

		URL url = new URL(
				"https://cdn.discordapp.com/attachments/717498714259980378/1084238741838757971/wheresthefire.mp4");

//		try (AsyncProcessInputStream p = new AsyncProcessInputStream(url.openStream(), new ProcessBuilder("ffmpeg", "-hide_banner",
//				"-nostats", "-i", "-", "-af", "ebur128", "-f", "null", "-"), true)) {
//			// reader.lines().forEach(System.out::println);
//			// Files.copy(p, Path.of("output.txt"), StandardCopyOption.REPLACE_EXISTING);
//
//			// System.out.println(new String(p.readAllBytes()));
//			System.out.println(getSegments(p));
//		}

//		try (InputStream in = new ProcessInputStream(url.openStream(),
//				new ProcessBuilder(quickTimeBinaryPath.toString(), "-q"))) {
//			String data = new String(in.readAllBytes());
//			Files.write(Path.of("output.mp4"), data.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
//			//Files.copy(in, Path.of("output.mp4"), StandardCopyOption.REPLACE_EXISTING);
//		}
		try (ForkedInputStream in = new ForkedInputStream(new AsyncProcessInputStream(url.openStream(), new ProcessBuilder("ffmpeg", "-hide_banner",
				"-nostats", "-i", "-", "-af", "ebur128", "-f", "null", "-"), true)); InputStream in2 = in.getForkedStream()) {
			CompletableFuture.allOf(CompletableFuture.runAsync(() -> {
				try {
					System.out.println(getSegments(in));
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}), CompletableFuture.runAsync(() -> {
				try {
					System.out.println(getSegments(in2));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			})).join();
//			System.out.println(getSegments(in));
//			System.out.println(getSegments(in2));
		}
	}

	private static List<Double> getSegments(InputStream stream) throws IOException {
		List<Double> list = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
			list = reader.lines().filter(s -> s.startsWith("[Parsed_ebur128_0")).map(line -> {
				int start = line.indexOf('M', EBUR128) + 2;

				if (start < 2)
					return Double.NaN;

				int end = line.indexOf('S', start);
				String loudStr = line.substring(start, end);

				try {
					return Double.parseDouble(loudStr);

				} catch (NumberFormatException ex) {
					System.err.println("Bad double value " + loudStr + " skipping...");
					return Double.NaN;
				}
			}).filter(d -> d != Double.NaN).toList();
		}
		return list;
	}
}
