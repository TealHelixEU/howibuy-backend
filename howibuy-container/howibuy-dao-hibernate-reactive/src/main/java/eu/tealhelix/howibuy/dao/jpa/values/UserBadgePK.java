package eu.tealhelix.howibuy.dao.jpa.values;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import eu.tealhelix.howibuy.v1.types.Badge;

public class UserBadgePK implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	private UUID userProfileId;
	private Badge badge;

	public UserBadgePK() {
		// NOOP
	}

	public UserBadgePK(UUID userProfileId, Badge badge) {
		this.userProfileId = userProfileId;
		this.badge = badge;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof UserBadgePK that)) return false;
		return Objects.equals(getUserProfileId(), that.getUserProfileId()) && getBadge() == that.getBadge();
	}

	@Override
	public int hashCode() {
		return Objects.hash(getUserProfileId(), getBadge());
	}

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
}
