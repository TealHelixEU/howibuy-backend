package eu.tealhelix.catalogue.services.v1.impl;

/**
 * A Feed in the {@link FeedStore}.
 *
 * @param path   Where the Feed is stored, relative to the directory of the store
 * @param size   The size of the Feed as received, before compression, in bytes
 * @param sha256 The SHA-256 of the Feed as received, before compression, in lowercase hex
 */
public record StoredFeed(String path, long size, String sha256) {
}
