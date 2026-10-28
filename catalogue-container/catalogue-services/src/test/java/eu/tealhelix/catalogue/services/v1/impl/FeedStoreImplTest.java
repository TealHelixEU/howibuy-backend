package eu.tealhelix.catalogue.services.v1.impl;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import eu.tealhelix.catalogue.v1.types.FeedId;
import eu.tealhelix.catalogue.v1.types.impl.FeedIdImpl;
import eu.tealhelix.common.v1.types.RetailerId;
import eu.tealhelix.common.v1.types.impl.GenericRetailerId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Feeds land in the store whole, compressed and checksummed, and are written while they are read.
 */
public class FeedStoreImplTest {
	private static final String RETAILER_ID = "6f1f0b52-8a0e-4c1b-9d53-1c7a3e0b2f11";
	private static final RetailerId RETAILER = new GenericRetailerId(RETAILER_ID);
	private static final int MEGABYTE = 1024 * 1024;

	@TempDir
	Path directory;

	@Test
	void storesAFeedCompressedUnderItsRetailerAndId() throws IOException {
		var feedId = newFeedId();
		var sut = new FeedStoreImpl(directory.toString());

		var storedFeed = sut.store(RETAILER, feedId, new ByteArrayInputStream("<feed>test</feed>".getBytes(UTF_8)));

		assertEquals(new StoredFeed(RETAILER_ID + "/" + feedId.asString() + ".gz", 17, "372c3f4cc881b55653a9c026ce87f1455636e0a1fbea9f1af2b3435442a85bd1"), storedFeed);
		assertArrayEquals("<feed>test</feed>".getBytes(UTF_8), gunzip(directory.resolve(storedFeed.path())));
	}

	@Test
	void opensAStoredFeedAsReceived() throws IOException {
		var sut = new FeedStoreImpl(directory.toString());
		var storedFeed = sut.store(RETAILER, newFeedId(), new ByteArrayInputStream("<feed>test</feed>".getBytes(UTF_8)));

		try (var feed = sut.open(storedFeed.path())) {
			assertArrayEquals("<feed>test</feed>".getBytes(UTF_8), feed.readAllBytes());
		}
	}

	@Test
	void aMissingFeedCannotBeOpened() {
		var sut = new FeedStoreImpl(directory.toString());

		assertThrows(UncheckedIOException.class, () -> sut.open("test-gr/missing.gz"));
	}

	@Test
	void createsTheDirectoryOfTheStoreIfMissing() throws IOException {
		var sut = new FeedStoreImpl(directory.resolve("not-yet").toString());

		var storedFeed = sut.store(RETAILER, newFeedId(), new ByteArrayInputStream(new byte[]{1, 2, 3}));

		assertArrayEquals(new byte[]{1, 2, 3}, gunzip(directory.resolve("not-yet").resolve(storedFeed.path())));
	}

	@Test
	void writesTheFeedWhileReadingIt() {
		var sut = new FeedStoreImpl(directory.toString());
		var body = new RandomBytes(16 * MEGABYTE, 8 * MEGABYTE, () -> assertTrue(bytesOnDisk() > MEGABYTE, "more than a megabyte written after reading half of the Feed"));

		var storedFeed = sut.store(RETAILER, newFeedId(), body);

		assertTrue(body.checked, "the check ran");
		assertEquals(16 * MEGABYTE, storedFeed.size());
	}

	@Test
	void aFeedThatCannotBeReadWholeLeavesNothingInTheStore() throws IOException {
		var sut = new FeedStoreImpl(directory.toString());
		var body = new RandomBytes(16 * MEGABYTE, 8 * MEGABYTE, () -> {
			throw new IOException("Connection reset");
		});

		assertThrows(UncheckedIOException.class, () -> sut.store(RETAILER, newFeedId(), body));

		assertEquals(List.of(), regularFiles());
	}

	@Test
	void deletesAStoredFeed() throws IOException {
		var sut = new FeedStoreImpl(directory.toString());
		var storedFeed = sut.store(RETAILER, newFeedId(), new ByteArrayInputStream(new byte[]{1}));

		sut.delete(storedFeed.path());

		assertFalse(Files.exists(directory.resolve(storedFeed.path())));
	}

	@Test
	void deletingAFeedThatIsNotStoredDoesNothing() {
		var sut = new FeedStoreImpl(directory.toString());

		sut.delete(RETAILER_ID + "/" + newFeedId().asString() + ".gz");
	}

	@Test
	void tellsWhetherAFeedIsStored() {
		var sut = new FeedStoreImpl(directory.toString());
		var storedFeed = sut.store(RETAILER, newFeedId(), new ByteArrayInputStream(new byte[]{1}));

		assertTrue(sut.exists(storedFeed.path()));
		sut.delete(storedFeed.path());
		assertFalse(sut.exists(storedFeed.path()));
	}

	private static FeedId newFeedId() {
		return new FeedIdImpl(UUID.randomUUID().toString());
	}

	private static byte[] gunzip(Path file) throws IOException {
		try (var in = new GZIPInputStream(Files.newInputStream(file))) {
			return in.readAllBytes();
		}
	}

	private List<Path> regularFiles() throws IOException {
		try (Stream<Path> files = Files.walk(directory)) {
			return files.filter(Files::isRegularFile).toList();
		}
	}

	private long bytesOnDisk() {
		try {
			long total = 0;
			for (var file : regularFiles()) total += Files.size(file);
			return total;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Incompressible bytes, made as they are read, that run a check once a given number of them has been read.
	 */
	private static class RandomBytes extends InputStream {
		private final Random random = new Random(42);
		private final long length;
		private final long checkAt;
		private final Check check;
		private long position;
		boolean checked;

		RandomBytes(long length, long checkAt, Check check) {
			this.length = length;
			this.checkAt = checkAt;
			this.check = check;
		}

		@Override
		public int read() throws IOException {
			var one = new byte[1];
			return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			if (position >= length) return -1;
			if (!checked && position >= checkAt) {
				checked = true;
				check.run();
			}
			var count = (int) Math.min(len, (position < checkAt ? checkAt : length) - position);
			var bytes = new byte[count];
			random.nextBytes(bytes);
			System.arraycopy(bytes, 0, b, off, count);
			position += count;
			return count;
		}
	}

	private interface Check {
		void run() throws IOException;
	}
}
