package eu.tealhelix.howibuy.dao.jpa;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import eu.tealhelix.howibuy.dao.jpa.values.PurchasedBasketProductPK;

@Entity
@Table(name = "TH_PURCHASED_BASKET_PRODUCT")
@IdClass(PurchasedBasketProductPK.class)
public class PurchasedBasketProductEntity {
	@Id
	@Column(name = "purchased_basket_id")
	private UUID purchasedBasketId;

	@Id
	@Column(name = "product_key")
	private String productKey;

	@Column(name = "archetype_product_id")
	private UUID archetypeProductId;

	@Column(name = "personal_overall_score")
	private double personalOverallScore;

	@Column(name = "scientific_overall_score")
	private double scientificOverallScore;

	@Column(name = "score_environment")
	private double scoreEnvironment;

	@Column(name = "score_animal_welfare")
	private double scoreAnimalWelfare;

	@Column(name = "score_socio_economic")
	private double scoreSocioEconomic;

	@Column(name = "score_health")
	private double scoreHealth;

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

	public UUID getArchetypeProductId() {
		return archetypeProductId;
	}

	public void setArchetypeProductId(UUID archetypeProductId) {
		this.archetypeProductId = archetypeProductId;
	}

	public double getPersonalOverallScore() {
		return personalOverallScore;
	}

	public void setPersonalOverallScore(double personalOverallScore) {
		this.personalOverallScore = personalOverallScore;
	}

	public double getScientificOverallScore() {
		return scientificOverallScore;
	}

	public void setScientificOverallScore(double scientificOverallScore) {
		this.scientificOverallScore = scientificOverallScore;
	}

	public double getScoreEnvironment() {
		return scoreEnvironment;
	}

	public void setScoreEnvironment(double scoreEnvironment) {
		this.scoreEnvironment = scoreEnvironment;
	}

	public double getScoreAnimalWelfare() {
		return scoreAnimalWelfare;
	}

	public void setScoreAnimalWelfare(double scoreAnimalWelfare) {
		this.scoreAnimalWelfare = scoreAnimalWelfare;
	}

	public double getScoreSocioEconomic() {
		return scoreSocioEconomic;
	}

	public void setScoreSocioEconomic(double scoreSocioEconomic) {
		this.scoreSocioEconomic = scoreSocioEconomic;
	}

	public double getScoreHealth() {
		return scoreHealth;
	}

	public void setScoreHealth(double scoreHealth) {
		this.scoreHealth = scoreHealth;
	}
}
