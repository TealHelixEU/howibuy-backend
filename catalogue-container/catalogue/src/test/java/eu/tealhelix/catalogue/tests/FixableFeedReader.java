package eu.tealhelix.catalogue.tests;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.stream.Stream;
import jakarta.enterprise.context.ApplicationScoped;

import eu.tealhelix.catalogue.feeds.DeletionItem;
import eu.tealhelix.catalogue.feeds.FeedItem;
import eu.tealhelix.catalogue.feeds.FeedReader;
import eu.tealhelix.catalogue.feeds.jsonl.JsonLinesFeedReader;

/**
 * An adapter of the tests, named {@value #NAME}, that a test breaks and fixes: version {@value #BROKEN} cannot read any
 * Feed, and version {@value #FIXED} reads the Feeds of {@link JsonLinesFeedReader}. It is fixed until a test breaks it.
 */
@ApplicationScoped
public class FixableFeedReader implements FeedReader {
	static final String NAME = "test-fixable";
	static final String BROKEN = "1";
	static final String FIXED = "2";
	static final String FAULT = "The broken adapter reads no Feed";

	private final JsonLinesFeedReader fixed = new JsonLinesFeedReader();
	private volatile boolean broken;

	void setBroken(boolean broken) {
		this.broken = broken;
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public String getVersion() {
		return broken ? BROKEN : FIXED;
	}

	@Override
	public Stream<FeedItem> read(InputStream feed) {
		if (broken) throw new UncheckedIOException(FAULT, new IOException(FAULT));
		return fixed.read(feed);
	}

	@Override
	public Stream<DeletionItem> readDeletions(InputStream feed) {
		if (broken) throw new UncheckedIOException(FAULT, new IOException(FAULT));
		return fixed.readDeletions(feed);
	}
}
