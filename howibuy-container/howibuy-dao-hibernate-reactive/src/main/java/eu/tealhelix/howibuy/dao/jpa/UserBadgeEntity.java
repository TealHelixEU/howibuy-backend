package eu.tealhelix.howibuy.dao.jpa;

import static jakarta.persistence.EnumType.STRING;

import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import eu.tealhelix.howibuy.dao.jpa.values.UserBadgePK;
import eu.tealhelix.howibuy.v1.types.Badge;

@Entity
@Table(name = "TH_USER_BADGE")
@IdClass(UserBadgePK.class)
public class UserBadgeEntity {
	@Id
	@Column(name = "user_profile_id")
	private UUID userProfileId;

	@Id
	@Column(name = "badge")
	@Enumerated(STRING)
	private Badge badge;

	@Column(name = "purchased_basket_id")
	private UUID purchasedBasketId;

	@Column(name = "earned_at")
	private LocalDateTime earnedAt;

	public UUID getUserProfileId() {
		return userProfileId;
	}

	public void setUserProfileId(UUID userProfileId) {
		this.userProfileId = userProfileId;
	}

	public Badge getBadge() {
		return badge;
	}

	public void setBadge(Badge badge) {
		this.badge = badge;
	}

	public UUID getPurchasedBasketId() {
		return purchasedBasketId;
	}

	public void setPurchasedBasketId(UUID purchasedBasketId) {
		this.purchasedBasketId = purchasedBasketId;
	}

	public LocalDateTime getEarnedAt() {
		return earnedAt;
	}

	public void setEarnedAt(LocalDateTime earnedAt) {
		this.earnedAt = earnedAt;
	}
}
