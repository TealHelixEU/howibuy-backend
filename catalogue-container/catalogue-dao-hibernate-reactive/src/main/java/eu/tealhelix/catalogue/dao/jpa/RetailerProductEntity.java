package eu.tealhelix.catalogue.dao.jpa;

import static jakarta.persistence.CascadeType.ALL;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.MapKey;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "IN_RETAILER_PRODUCT")
public class RetailerProductEntity {
	@Id
	@Column(name = "id")
	private UUID id;

	@Column(name = "retailer_id")
	private UUID retailerId;

	@Column(name = "product_key")
	private String productKey;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "characteristics")
	private Map<String, String> characteristics;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "tags")
	private List<String> tags;

	@Column(name = "input_hash")
	private String inputHash;

	@Column(name = "change_sequence")
	private long changeSequence;

	@Column(name = "removed")
	private boolean removed;

	@OneToMany(mappedBy = "retailerProduct", cascade = ALL, orphanRemoval = true)
	@MapKey(name = "lang")
	private Map<String, RetailerProductNameEntity> names = new HashMap<>();

	@OneToMany(mappedBy = "retailerProduct", cascade = ALL, orphanRemoval = true)
	@MapKey(name = "catalogueId")
	private Map<String, ListingEntity> listings = new HashMap<>();

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getRetailerId() {
		return retailerId;
	}

	public void setRetailerId(UUID retailerId) {
		this.retailerId = retailerId;
	}

	public String getProductKey() {
		return productKey;
	}

	public void setProductKey(String productKey) {
		this.productKey = productKey;
	}

	public Map<String, String> getCharacteristics() {
		return characteristics;
	}

	public void setCharacteristics(Map<String, String> characteristics) {
		this.characteristics = characteristics;
	}

	public List<String> getTags() {
		return tags;
	}

	public void setTags(List<String> tags) {
		this.tags = tags;
	}

	public String getInputHash() {
		return inputHash;
	}

	public void setInputHash(String inputHash) {
		this.inputHash = inputHash;
	}

	public long getChangeSequence() {
		return changeSequence;
	}

	public void setChangeSequence(long changeSequence) {
		this.changeSequence = changeSequence;
	}

	public boolean isRemoved() {
		return removed;
	}

	public void setRemoved(boolean removed) {
		this.removed = removed;
	}

	public Map<String, RetailerProductNameEntity> getNames() {
		return names;
	}

	public void setNames(Map<String, RetailerProductNameEntity> names) {
		this.names = names;
	}

	public Map<String, ListingEntity> getListings() {
		return listings;
	}

	public void setListings(Map<String, ListingEntity> listings) {
		this.listings = listings;
	}
}
