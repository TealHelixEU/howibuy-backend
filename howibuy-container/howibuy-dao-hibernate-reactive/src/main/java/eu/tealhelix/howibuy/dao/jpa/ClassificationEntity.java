package eu.tealhelix.howibuy.dao.jpa;

import static jakarta.persistence.EnumType.STRING;

import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import eu.tealhelix.howibuy.v1.types.Classifier;

@Entity
@Table(name = "TH_CLASSIFICATION")
public class ClassificationEntity {
	@Id
	@Column(name = "retailer_product_id")
	private UUID retailerProductId;

	@Column(name = "classifier")
	@Enumerated(STRING)
	private Classifier classifier;

	@Column(name = "archetype_product_id")
	private UUID archetypeProductId;

	@Column(name = "input_hash")
	private String inputHash;

	@Column(name = "needs_review")
	private boolean needsReview;

	@Column(name = "resolved_at")
	private LocalDateTime resolvedAt;

	@Column(name = "reopened")
	private boolean reopened;

	public UUID getRetailerProductId() {
		return retailerProductId;
	}

	public void setRetailerProductId(UUID retailerProductId) {
		this.retailerProductId = retailerProductId;
	}

	public Classifier getClassifier() {
		return classifier;
	}

	public void setClassifier(Classifier classifier) {
		this.classifier = classifier;
	}

	public UUID getArchetypeProductId() {
		return archetypeProductId;
	}

	public void setArchetypeProductId(UUID archetypeProductId) {
		this.archetypeProductId = archetypeProductId;
	}

	public String getInputHash() {
		return inputHash;
	}

	public void setInputHash(String inputHash) {
		this.inputHash = inputHash;
	}

	public boolean isNeedsReview() {
		return needsReview;
	}

	public void setNeedsReview(boolean needsReview) {
		this.needsReview = needsReview;
	}

	public LocalDateTime getResolvedAt() {
		return resolvedAt;
	}

	public void setResolvedAt(LocalDateTime resolvedAt) {
		this.resolvedAt = resolvedAt;
	}

	public boolean isReopened() {
		return reopened;
	}

	public void setReopened(boolean reopened) {
		this.reopened = reopened;
	}
}
