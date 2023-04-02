package net.foxgenesis.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.foxgenesis.util.MethodTimer;
import net.foxgenesis.watame.filescanner.pipe.AsyncProcessPipe;
import net.foxgenesis.watame.filescanner.pipe.DuplicatePipe;
import net.foxgenesis.watame.filescanner.pipe.ProcessPipe;

public class PipeTest {
	private static final int EBUR128 = 38;

	public static void main(String[] args) throws Exception {
		Path quickTimeBinaryPath = Path.of("lib", "qt-faststart-x86.exe");

		URL url = new URL(
				"https://cdn.discordapp.com/attachments/717498714259980378/1084238741838757971/wheresthefire.mp4");
		System.out.println(
				"Total time: " + MethodTimer.runFormatSec(() -> run(quickTimeBinaryPath, url), 10, 2) + " seconds");;
	}

	@SuppressWarnings("resource")
	private static void run(Path quickTimeBinaryPath, URL url) {
		try (DuplicatePipe pipe = new DuplicatePipe(new AsyncProcessPipe(
				new ProcessBuilder("ffmpeg", "-hide_banner", "-nostats", "-i", "-", "-af", "ebur128", "-f", "null",
						"-"),
				true, new ProcessPipe(new ProcessBuilder(quickTimeBinaryPath.toString(), "-q"), url.openStream())));
				PipedInputStream in = pipe.getConnection()) {
			pipe.pipeData();
			getSegments(in);
		} catch (Exception e) {
			e.printStackTrace();
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
