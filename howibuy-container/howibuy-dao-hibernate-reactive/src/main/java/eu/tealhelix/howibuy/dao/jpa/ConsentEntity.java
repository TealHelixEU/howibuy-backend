package eu.tealhelix.howibuy.dao.jpa;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;

import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import eu.tealhelix.howibuy.dao.jpa.values.ConsentPK;
import eu.tealhelix.howibuy.dao.jpa.values.ConsentState;

@Entity
@Table(name = "TH_CONSENT")
@IdClass(ConsentPK.class)
public class ConsentEntity {
	@Id
	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "user_id")
	private UserProfileEntity user;

	@Id
	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "retailer_id")
	private RetailerEntity retailer;

	@Enumerated(STRING)
	private ConsentState state;

	public UserProfileEntity getUser() {
		return user;
	}

	public void setUser(UserProfileEntity user) {
		this.user = user;
	}

	public RetailerEntity getRetailer() {
		return retailer;
	}

	public void setRetailer(RetailerEntity retailer) {
		this.retailer = retailer;
	}

	public ConsentState getState() {
		return state;
	}

	public void setState(ConsentState state) {
		this.state = state;
	}
}
