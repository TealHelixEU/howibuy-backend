package eu.tealhelix.howibuy.dao.jpa.values;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class PurchasedBasketProductPK implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	private UUID purchasedBasketId;
	private String productKey;

	public PurchasedBasketProductPK() {
		// NOOP
	}

	public PurchasedBasketProductPK(UUID purchasedBasketId, String productKey) {
		this.purchasedBasketId = purchasedBasketId;
		this.productKey = productKey;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof PurchasedBasketProductPK that)) return false;
		return Objects.equals(getPurchasedBasketId(), that.getPurchasedBasketId()) && Objects.equals(getProductKey(), that.getProductKey());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getPurchasedBasketId(), getProductKey());
	}

	public UUID getPurchasedBasketId() {
		return purchasedBasketId;
	}

	public void setPurchasedBasketId(UUID purchasedBasketId) {
		this.purchasedBasketId = purchasedBasketId;
	}

	public String getProductKey() {
		return productKey;
	}

	public void setProductKey(String productKey) {
		this.productKey = productKey;
	}
}
