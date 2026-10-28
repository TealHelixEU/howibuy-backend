package eu.tealhelix.catalogue.dao.jpa;

import static jakarta.persistence.EnumType.STRING;

import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import eu.tealhelix.catalogue.v1.types.FeedType;

@Entity
@Table(name = "IN_FEED")
public class FeedEntity {
	@Id
	@Column(name = "id")
	private UUID id;

	@Column(name = "retailer_id")
	private UUID retailerId;

	@Enumerated(STRING)
	@Column(name = "feed_type")
	private FeedType feedType;

	@Column(name = "received_at")
	private LocalDateTime receivedAt;

	@Column(name = "file_path")
	private String filePath;

	@Column(name = "file_size")
	private long fileSize;

	@Column(name = "file_sha256")
	private String fileSha256;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getRetailerId() {
		return retailerId;
	}

	public void setRetailerId(UUID retailerId) {
		this.retailerId = retailerId;
	}

	public FeedType getFeedType() {
		return feedType;
	}

	public void setFeedType(FeedType feedType) {
		this.feedType = feedType;
	}

	public LocalDateTime getReceivedAt() {
		return receivedAt;
	}

	public void setReceivedAt(LocalDateTime receivedAt) {
		this.receivedAt = receivedAt;
	}

	public String getFilePath() {
		return filePath;
	}

	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}

	public long getFileSize() {
		return fileSize;
	}

	public void setFileSize(long fileSize) {
		this.fileSize = fileSize;
	}

	public String getFileSha256() {
		return fileSha256;
	}

	public void setFileSha256(String fileSha256) {
		this.fileSha256 = fileSha256;
	}
}
