package eu.tealhelix.sfc.dao.jpa;

import static jakarta.persistence.EnumType.STRING;

import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import eu.tealhelix.sfc.v1.types.AttemptStatus;

/**
 * A user's round of the compass. The {@link #getUserId() user} is held as a raw UUID with only a DB-level foreign key
 * to the user profile and no JPA association, keeping this module free of HowiBuy persistence code. An attempt is
 * {@link AttemptStatus#IN_PROGRESS} while it collects answers and freezes to {@link AttemptStatus#COMPLETED} once the
 * user locks it, stamping {@link #getCompletedAt() completedAt}.
 */
@Entity
@Table(name = "TH_SFC_ATTEMPT")
public class AttemptEntity {
	@Id
	@Column(name = "id")
	private UUID id;

	@Column(name = "user_id")
	private UUID userId;

	@Enumerated(STRING)
	@Column(name = "status")
	private AttemptStatus status;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getUserId() {
		return userId;
	}

	public void setUserId(UUID userId) {
		this.userId = userId;
	}

	public AttemptStatus getStatus() {
		return status;
	}

	public void setStatus(AttemptStatus status) {
		this.status = status;
	}

	public LocalDateTime getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(LocalDateTime completedAt) {
		this.completedAt = completedAt;
	}
}
