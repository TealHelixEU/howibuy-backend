package eu.tealhelix.catalogue.dao.jpa.values;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class ListingPK implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	private UUID retailerProduct;
	private String catalogueId;

	public ListingPK() {
		// NOOP
	}

	public ListingPK(UUID retailerProduct, String catalogueId) {
		this.retailerProduct = retailerProduct;
		this.catalogueId = catalogueId;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof ListingPK that)) return false;
		return Objects.equals(getRetailerProduct(), that.getRetailerProduct()) && Objects.equals(getCatalogueId(), that.getCatalogueId());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getRetailerProduct(), getCatalogueId());
	}

	public UUID getRetailerProduct() {
		return retailerProduct;
	}

	public void setRetailerProduct(UUID retailerProduct) {
		this.retailerProduct = retailerProduct;
	}

	public String getCatalogueId() {
		return catalogueId;
	}

	public void setCatalogueId(String catalogueId) {
		this.catalogueId = catalogueId;
	}
}
