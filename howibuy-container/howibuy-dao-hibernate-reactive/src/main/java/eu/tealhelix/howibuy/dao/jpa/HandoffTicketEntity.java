package eu.tealhelix.howibuy.dao.jpa;

import static jakarta.persistence.FetchType.LAZY;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "TH_HANDOFF_TICKET")
public class HandoffTicketEntity {
	@Id
	@Column(name = "ticket_hash")
	private String ticketHash;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "user_id")
	private UserProfileEntity user;

	@Column(name = "expires_at")
	private LocalDateTime expiresAt;

	@Column(name = "consumed_at")
	private LocalDateTime consumedAt;

	public String getTicketHash() {
		return ticketHash;
	}

	public void setTicketHash(String ticketHash) {
		this.ticketHash = ticketHash;
	}

	public UserProfileEntity getUser() {
		return user;
	}

	public void setUser(UserProfileEntity user) {
		this.user = user;
	}

	public LocalDateTime getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(LocalDateTime expiresAt) {
		this.expiresAt = expiresAt;
	}

	public LocalDateTime getConsumedAt() {
		return consumedAt;
	}

	public void setConsumedAt(LocalDateTime consumedAt) {
		this.consumedAt = consumedAt;
	}
}
