package eu.tealhelix.catalogue.feeds;

import java.io.InputStream;
import java.util.stream.Stream;

/**
 * Reads the Feeds of one Retailer format. It parses and maps, and that is all: it has no database access and does no
 * diffing. The Catalogue's configuration names the reader of its Feeds.
 */
public interface FeedReader {
	/**
	 * The name the Catalogues use to choose this reader.
	 */
	String getName();

	/**
	 * The version of this reader, recorded with each Ingestion Run it reads.
	 */
	String getVersion();

	/**
	 * Read a Feed lazily, one item at a time, in the order of the Feed. A record that cannot be read becomes a
	 * {@link RejectedRecord}, and reading goes on with the next.
	 *
	 * @param feed The Feed as the Retailer sent it; the caller closes it
	 * @return The items of the Feed, positioned from 1
	 * @throws java.io.UncheckedIOException If the Feed cannot be read at all, also while the stream is consumed
	 */
	Stream<FeedItem> read(InputStream feed);
}
