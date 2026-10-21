package eu.tealhelix.catalogue.dao.jpa;

import static jakarta.persistence.FetchType.LAZY;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import eu.tealhelix.catalogue.dao.jpa.values.ListingPK;

@Entity
@Table(name = "IN_LISTING")
@IdClass(ListingPK.class)
public class ListingEntity {
	@Id
	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "retailer_product_id")
	private RetailerProductEntity retailerProduct;

	@Id
	@Column(name = "catalogue_id")
	private String catalogueId;

	@Column(name = "price")
	private BigDecimal price;

	@Column(name = "currency")
	private String currency;

	@Column(name = "active")
	private boolean active;

	@Column(name = "last_seen_at")
	private LocalDateTime lastSeenAt;

	@Column(name = "last_seen_run_id")
	private UUID lastSeenRunId;

	public RetailerProductEntity getRetailerProduct() {
		return retailerProduct;
	}

	public void setRetailerProduct(RetailerProductEntity retailerProduct) {
		this.retailerProduct = retailerProduct;
	}

	public String getCatalogueId() {
		return catalogueId;
	}

	public void setCatalogueId(String catalogueId) {
		this.catalogueId = catalogueId;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public LocalDateTime getLastSeenAt() {
		return lastSeenAt;
	}

	public void setLastSeenAt(LocalDateTime lastSeenAt) {
		this.lastSeenAt = lastSeenAt;
	}

	public UUID getLastSeenRunId() {
		return lastSeenRunId;
	}

	public void setLastSeenRunId(UUID lastSeenRunId) {
		this.lastSeenRunId = lastSeenRunId;
	}
}
