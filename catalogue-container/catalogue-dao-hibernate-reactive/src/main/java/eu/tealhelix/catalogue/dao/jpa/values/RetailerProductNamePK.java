package eu.tealhelix.catalogue.dao.jpa.values;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class RetailerProductNamePK implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	private UUID retailerProduct;
	private String lang;

	public RetailerProductNamePK() {
		// NOOP
	}

	public RetailerProductNamePK(UUID retailerProduct, String lang) {
		this.retailerProduct = retailerProduct;
		this.lang = lang;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof RetailerProductNamePK that)) return false;
		return Objects.equals(getRetailerProduct(), that.getRetailerProduct()) && Objects.equals(getLang(), that.getLang());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getRetailerProduct(), getLang());
	}

	public UUID getRetailerProduct() {
		return retailerProduct;
	}

	public void setRetailerProduct(UUID retailerProduct) {
		this.retailerProduct = retailerProduct;
	}

	public String getLang() {
		return lang;
	}

	public void setLang(String lang) {
		this.lang = lang;
	}
}
