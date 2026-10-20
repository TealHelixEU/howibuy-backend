package eu.tealhelix.catalogue.services.v1.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.GZIPOutputStream;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.IngestionRunId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Keeps the Feeds in a directory, one subdirectory per Catalogue. A Feed is written to a temporary file in the directory
 * while it is read, and moved to its place once read whole, so a Feed in its place is always complete.
 */
@ApplicationScoped
public class FeedStoreImpl implements FeedStore {
	private static final int BUFFER_SIZE = 64 * 1024;

	private final Path directory;

	/**
	 * Injection constructor.
	 *
	 * @param directory The directory of the store; it is created if missing
	 */
	@Inject
	public FeedStoreImpl(@ConfigProperty(name = "catalogue.feed-store.directory") String directory) {
		this.directory = Path.of(directory).toAbsolutePath().normalize();
	}

	@Override
	public StoredFeed store(CatalogueId catalogueId, IngestionRunId runId, InputStream body) {
		var path = catalogueId.asString() + "/" + runId.asString() + ".gz";
		var target = resolve(path);
		Path temporary = null;
		var stored = false;
		try {
			Files.createDirectories(target.getParent());
			temporary = Files.createTempFile(directory, "receiving-", ".gz");
			var digest = sha256();
			long size;
			try (var out = new GZIPOutputStream(Files.newOutputStream(temporary), BUFFER_SIZE)) {
				size = copy(body, out, digest);
			}
			Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
			stored = true;
			return new StoredFeed(path, size, HexFormat.of().formatHex(digest.digest()));
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to store a Feed, path: " + path, e);
		} finally {
			if (!stored && temporary != null) {
				deleteIfExists(temporary);
			}
		}
	}

	@Override
	public void delete(String path) {
		deleteIfExists(resolve(path));
	}

	/**
	 * The place of a Feed in the directory, which a path may not lead out of.
	 */
	private Path resolve(String path) {
		var resolved = directory.resolve(path).normalize();
		if (!resolved.startsWith(directory)) {
			throw new IllegalArgumentException("A Feed must be inside the directory of the store, path: " + path);
		}
		return resolved;
	}

	private static long copy(InputStream in, OutputStream out, MessageDigest digest) throws IOException {
		var buffer = new byte[BUFFER_SIZE];
		long size = 0;
		int count;
		while ((count = in.read(buffer)) >= 0) {
			digest.update(buffer, 0, count);
			out.write(buffer, 0, count);
			size += count;
		}
		return size;
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("Every Java platform implements SHA-256", e);
		}
	}

	private static void deleteIfExists(Path file) {
		try {
			Files.deleteIfExists(file);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to delete a Feed, file: " + file, e);
		}
	}
}
