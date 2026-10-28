package eu.tealhelix.catalogue.dao.impl;

import jakarta.enterprise.context.ApplicationScoped;

import eu.tealhelix.catalogue.dao.FeedDao;
import eu.tealhelix.catalogue.dao.jpa.FeedEntity;
import eu.tealhelix.catalogue.v1.model.Feed;
import eu.tealhelix.catalogue.v1.model.ImmutableFeed;
import eu.tealhelix.catalogue.v1.types.HasFeedId;
import eu.tealhelix.catalogue.v1.types.impl.FeedIdImpl;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.v1.types.impl.GenericRetailerId;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class FeedDaoImpl implements FeedDao {
	@Override
	public Uni<Void> create(ReactivePersistenceTxContext tx, Feed feed) {
		return tx.persist(toEntity(feed)).replaceWithVoid();
	}

	@Override
	public Uni<Feed> find(ReactivePersistenceContext em, HasFeedId feedId) {
		return em.find(FeedEntity.class, feedId.getId().asUuid()).map(entity -> entity != null ? toFeed(entity) : null);
	}

	private static FeedEntity toEntity(Feed feed) {
		var entity = new FeedEntity();
		entity.setId(feed.getId().asUuid());
		entity.setRetailerId(feed.getRetailerId().asUuid());
		entity.setFeedType(feed.getFeedType());
		entity.setReceivedAt(feed.getReceivedAt());
		entity.setFilePath(feed.getFilePath());
		entity.setFileSize(feed.getFileSize());
		entity.setFileSha256(feed.getFileSha256());
		return entity;
	}

	/**
	 * Also maps the Feed that a run is read with.
	 */
	static Feed toFeed(FeedEntity entity) {
		return ImmutableFeed.builder()
				.id(new FeedIdImpl(entity.getId().toString()))
				.retailerId(new GenericRetailerId(entity.getRetailerId().toString()))
				.feedType(entity.getFeedType())
				.receivedAt(entity.getReceivedAt())
				.filePath(entity.getFilePath())
				.fileSize(entity.getFileSize())
				.fileSha256(entity.getFileSha256())
				.build();
	}
}
