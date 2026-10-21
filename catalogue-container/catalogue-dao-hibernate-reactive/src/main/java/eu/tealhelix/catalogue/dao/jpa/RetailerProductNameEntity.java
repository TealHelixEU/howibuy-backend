package eu.tealhelix.catalogue.dao.jpa;

import static jakarta.persistence.FetchType.LAZY;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import eu.tealhelix.catalogue.dao.jpa.values.RetailerProductNamePK;

@Entity
@Table(name = "IN_RETAILER_PRODUCT_NAME")
@IdClass(RetailerProductNamePK.class)
public class RetailerProductNameEntity {
	@Id
	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "retailer_product_id")
	private RetailerProductEntity retailerProduct;

	@Id
	@Column(name = "lang")
	private String lang;

	@Column(name = "name")
	private String name;

	public RetailerProductEntity getRetailerProduct() {
		return retailerProduct;
	}

	public void setRetailerProduct(RetailerProductEntity retailerProduct) {
		this.retailerProduct = retailerProduct;
	}

	public String getLang() {
		return lang;
	}

	public void setLang(String lang) {
		this.lang = lang;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}
