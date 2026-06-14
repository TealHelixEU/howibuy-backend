package eu.tealhelix.howibuy.dao.jpa;

import static jakarta.persistence.FetchType.LAZY;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import eu.tealhelix.howibuy.dao.jpa.values.CorrelationIdPK;

@Entity
@Table(name = "TH_CORREL_ID")
@IdClass(CorrelationIdPK.class)
public class CorrelationIdEntity {
	@Id
	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "retailer_id")
	private RetailerEntity retailer;

	@Id
	@Column(name = "correlation_id")
	private String correlationId;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "user_id")
	private UserProfileEntity user;

	public RetailerEntity getRetailer() {
		return retailer;
	}

	public void setRetailer(RetailerEntity retailer) {
		this.retailer = retailer;
	}

	public String getCorrelationId() {
		return correlationId;
	}

	public void setCorrelationId(String correlationId) {
		this.correlationId = correlationId;
	}

	public UserProfileEntity getUser() {
		return user;
	}

	public void setUser(UserProfileEntity user) {
		this.user = user;
	}
}
