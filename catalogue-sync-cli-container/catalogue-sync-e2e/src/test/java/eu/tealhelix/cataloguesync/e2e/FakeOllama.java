package eu.tealhelix.cataloguesync.e2e;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;

/**
 * Stands in for Ollama's chat API, {@code POST /api/chat} without streaming, and answers every chat with the same reply,
 * after failing the first chats it is told to fail with {@code 500}.
 */
final class FakeOllama implements AutoCloseable {
	private final HttpServer server;
	private final AtomicInteger chats = new AtomicInteger();

	/**
	 * @param reply The content of the assistant's message in every answer
	 */
	FakeOllama(String reply) throws IOException {
		this(reply, 0);
	}

	/**
	 * @param reply        The content of the assistant's message in every answer
	 * @param failingChats How many chats, from the first, to fail
	 */
	FakeOllama(String reply, int failingChats) throws IOException {
		var answer = """
				{"model": "fake", "created_at": "2026-10-26T10:00:00Z", "message": {"role": "assistant", "content": "%s"}, "done": true}"""
				.formatted(reply).getBytes(StandardCharsets.UTF_8);
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/api/chat", exchange -> {
			try (exchange) {
				exchange.getRequestBody().readAllBytes();
				if (chats.incrementAndGet() <= failingChats) {
					exchange.sendResponseHeaders(500, -1);
					return;
				}
				exchange.getResponseHeaders().add("Content-Type", "application/json");
				exchange.sendResponseHeaders(200, answer.length);
				exchange.getResponseBody().write(answer);
			}
		});
		server.start();
	}

	/**
	 * @return The base URL of the API
	 */
	String url() {
		return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
	}

	/**
	 * @return How many chats it has answered
	 */
	int chats() {
		return chats.get();
	}

	@Override
	public void close() {
		server.stop(0);
	}
}
