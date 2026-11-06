package eu.tealhelix.howibuy.dao.jpa;

import static jakarta.persistence.EnumType.STRING;

import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import eu.tealhelix.howibuy.v1.types.BasketAssessmentOutcomeType;
import eu.tealhelix.howibuy.v1.types.BasketMatch;

@Entity
@Table(name = "TH_PURCHASED_BASKET")
public class PurchasedBasketEntity {
	@Id
	@Column(name = "id")
	private UUID id;

	@Column(name = "user_profile_id")
	private UUID userProfileId;

	@Column(name = "retailer_id")
	private UUID retailerId;

	@Column(name = "catalogue_id")
	private String catalogueId;

	@Column(name = "purchased_at")
	private LocalDateTime purchasedAt;

	@Column(name = "recorded_at")
	private LocalDateTime recordedAt;

	@Column(name = "outcome_type")
	@Enumerated(STRING)
	private BasketAssessmentOutcomeType outcomeType;

	@Column(name = "basket_match")
	@Enumerated(STRING)
	private BasketMatch basketMatch;

	@Column(name = "xp_value")
	private int xpValue;

	@Column(name = "compass_attempt_id")
	private UUID compassAttemptId;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getUserProfileId() {
		return userProfileId;
	}

	public void setUserProfileId(UUID userProfileId) {
		this.userProfileId = userProfileId;
	}

	public UUID getRetailerId() {
		return retailerId;
	}

	public void setRetailerId(UUID retailerId) {
		this.retailerId = retailerId;
	}

	public String getCatalogueId() {
		return catalogueId;
	}

	public void setCatalogueId(String catalogueId) {
		this.catalogueId = catalogueId;
	}

	public LocalDateTime getPurchasedAt() {
		return purchasedAt;
	}

	public void setPurchasedAt(LocalDateTime purchasedAt) {
		this.purchasedAt = purchasedAt;
	}

	public LocalDateTime getRecordedAt() {
		return recordedAt;
	}

	public void setRecordedAt(LocalDateTime recordedAt) {
		this.recordedAt = recordedAt;
	}

	public BasketAssessmentOutcomeType getOutcomeType() {
		return outcomeType;
	}

	public void setOutcomeType(BasketAssessmentOutcomeType outcomeType) {
		this.outcomeType = outcomeType;
	}

	public BasketMatch getBasketMatch() {
		return basketMatch;
	}

	public void setBasketMatch(BasketMatch basketMatch) {
		this.basketMatch = basketMatch;
	}

	public int getXpValue() {
		return xpValue;
	}

	public void setXpValue(int xpValue) {
		this.xpValue = xpValue;
	}

	public UUID getCompassAttemptId() {
		return compassAttemptId;
	}

	public void setCompassAttemptId(UUID compassAttemptId) {
		this.compassAttemptId = compassAttemptId;
	}
}
