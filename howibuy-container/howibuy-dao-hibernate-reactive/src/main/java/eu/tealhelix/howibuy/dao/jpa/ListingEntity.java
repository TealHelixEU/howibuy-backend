package eu.tealhelix.howibuy.dao.jpa;

import static jakarta.persistence.FetchType.LAZY;

import java.math.BigDecimal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import eu.tealhelix.howibuy.dao.jpa.values.ListingPK;

@Entity
@Table(name = "TH_LISTING")
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
}
