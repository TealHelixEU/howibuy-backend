package eu.tealhelix.cataloguesync.e2e;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A deployable running in a process of its own, from the jar the build made, configured with system properties. Its
 * output goes to a log file of its own, which keeps it out of the output of the tests.
 */
final class DeployableProcess implements AutoCloseable {
	private static final Duration START_TIMEOUT = Duration.ofMinutes(2);
	private static final Duration RUN_TIMEOUT = Duration.ofMinutes(1);

	private final String name;
	private final Process process;
	private final Path log;

	private DeployableProcess(String name, Process process, Path log) {
		this.name = name;
		this.process = process;
		this.log = log;
	}

	/**
	 * @param deployable The deployable, whose jar is the system property {@code e2e.<deployable>.jar}
	 * @param name       The name of the process, which names its log file
	 * @param config     The system properties of the process
	 * @param args       The arguments of the process
	 */
	static DeployableProcess start(String deployable, String name, Map<String, String> config, String... args) throws IOException {
		var command = new ArrayList<String>();
		command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
		config.forEach((key, value) -> command.add("-D" + key + "=" + value));
		command.add("-jar");
		command.add(requireSystemProperty("e2e." + deployable + ".jar"));
		command.addAll(List.of(args));
		var logs = Files.createDirectories(Path.of(requireSystemProperty("e2e.logs.directory")));
		var log = logs.resolve(name + ".log");
		var process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
		return new DeployableProcess(name, process, log);
	}

	/**
	 * @return A port that no process listens to, for a deployable to listen to
	 */
	static int freePort() throws IOException {
		try (var socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}

	/**
	 * Wait until the process answers a request to the URL with {@code 200}.
	 */
	void awaitReady(String url) throws InterruptedException {
		var deadline = Instant.now().plus(START_TIMEOUT);
		var request = HttpRequest.newBuilder(URI.create(url)).build();
		try (var client = HttpClient.newHttpClient()) {
			while (true) {
				if (!process.isAlive()) throw new AssertionError("The process exited before it was ready, name: " + name + ", log: " + log);
				try {
					if (client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200) return;
				} catch (IOException e) {
					// It does not listen yet
				}
				if (Instant.now().isAfter(deadline)) throw new AssertionError("The process was not ready in time, name: " + name + ", log: " + log);
				Thread.sleep(200);
			}
		}
	}

	/**
	 * Wait until the process exits.
	 *
	 * @return Its exit code
	 */
	int awaitExit() throws InterruptedException {
		if (!process.waitFor(RUN_TIMEOUT.toSeconds(), SECONDS)) throw new AssertionError("The process did not exit in time, name: " + name + ", log: " + log);
		return process.exitValue();
	}

	/**
	 * @return All the process has written so far
	 */
	String output() throws IOException {
		return Files.readString(log);
	}

	@Override
	public void close() throws InterruptedException {
		process.destroy();
		if (!process.waitFor(30, SECONDS)) process.destroyForcibly().waitFor();
	}

	static String requireSystemProperty(String key) {
		var value = System.getProperty(key);
		if (value == null) throw new IllegalStateException("A system property that the build sets is missing, key: " + key);
		return value;
	}
}
