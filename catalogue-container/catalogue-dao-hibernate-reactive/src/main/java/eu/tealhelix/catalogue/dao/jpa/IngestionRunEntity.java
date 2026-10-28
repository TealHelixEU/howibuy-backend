package eu.tealhelix.catalogue.dao.jpa;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;

import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import eu.tealhelix.catalogue.v1.types.IngestionRunStatus;

@Entity
@Table(name = "IN_INGESTION_RUN")
public class IngestionRunEntity {
	@Id
	@Column(name = "id")
	private UUID id;

	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "feed_id")
	private FeedEntity feed;

	@Column(name = "catalogue_id")
	private String catalogueId;

	@Enumerated(STRING)
	@Column(name = "status")
	private IngestionRunStatus status;

	@Column(name = "adapter")
	private String adapter;

	@Column(name = "adapter_version")
	private String adapterVersion;

	@Column(name = "started_at")
	private LocalDateTime startedAt;

	@Column(name = "finished_at")
	private LocalDateTime finishedAt;

	@Column(name = "count_total")
	private Integer countTotal;

	@Column(name = "count_valid")
	private Integer countValid;

	@Column(name = "count_rejected")
	private Integer countRejected;

	@Column(name = "count_added")
	private Integer countAdded;

	@Column(name = "count_changed")
	private Integer countChanged;

	@Column(name = "count_unchanged")
	private Integer countUnchanged;

	@Column(name = "count_missing")
	private Integer countMissing;

	@Column(name = "count_deactivated")
	private Integer countDeactivated;

	@Column(name = "error", length = Integer.MAX_VALUE)
	private String error;

	@Column(name = "replay_of_run_id")
	private UUID replayOfRunId;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getCatalogueId() {
		return catalogueId;
	}

	public void setCatalogueId(String catalogueId) {
		this.catalogueId = catalogueId;
	}

	public FeedEntity getFeed() {
		return feed;
	}

	public void setFeed(FeedEntity feed) {
		this.feed = feed;
	}

	public IngestionRunStatus getStatus() {
		return status;
	}

	public void setStatus(IngestionRunStatus status) {
		this.status = status;
	}

	public String getAdapter() {
		return adapter;
	}

	public void setAdapter(String adapter) {
		this.adapter = adapter;
	}

	public String getAdapterVersion() {
		return adapterVersion;
	}

	public void setAdapterVersion(String adapterVersion) {
		this.adapterVersion = adapterVersion;
	}

	public LocalDateTime getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(LocalDateTime startedAt) {
		this.startedAt = startedAt;
	}

	public LocalDateTime getFinishedAt() {
		return finishedAt;
	}

	public void setFinishedAt(LocalDateTime finishedAt) {
		this.finishedAt = finishedAt;
	}

	public Integer getCountTotal() {
		return countTotal;
	}

	public void setCountTotal(Integer countTotal) {
		this.countTotal = countTotal;
	}

	public Integer getCountValid() {
		return countValid;
	}

	public void setCountValid(Integer countValid) {
		this.countValid = countValid;
	}

	public Integer getCountRejected() {
		return countRejected;
	}

	public void setCountRejected(Integer countRejected) {
		this.countRejected = countRejected;
	}

	public Integer getCountAdded() {
		return countAdded;
	}

	public void setCountAdded(Integer countAdded) {
		this.countAdded = countAdded;
	}

	public Integer getCountChanged() {
		return countChanged;
	}

	public void setCountChanged(Integer countChanged) {
		this.countChanged = countChanged;
	}

	public Integer getCountUnchanged() {
		return countUnchanged;
	}

	public void setCountUnchanged(Integer countUnchanged) {
		this.countUnchanged = countUnchanged;
	}

	public Integer getCountMissing() {
		return countMissing;
	}

	public void setCountMissing(Integer countMissing) {
		this.countMissing = countMissing;
	}

	public Integer getCountDeactivated() {
		return countDeactivated;
	}

	public void setCountDeactivated(Integer countDeactivated) {
		this.countDeactivated = countDeactivated;
	}

	public String getError() {
		return error;
	}

	public void setError(String error) {
		this.error = error;
	}

	public UUID getReplayOfRunId() {
		return replayOfRunId;
	}

	public void setReplayOfRunId(UUID replayOfRunId) {
		this.replayOfRunId = replayOfRunId;
	}
}
