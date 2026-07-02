package eu.tealhelix.howibuy.dao.jpa;

import static jakarta.persistence.FetchType.LAZY;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import eu.tealhelix.howibuy.dao.jpa.values.AnimalWelfareImpact;
import eu.tealhelix.howibuy.dao.jpa.values.EnvironmentalImpact;
import eu.tealhelix.howibuy.dao.jpa.values.SocialImpact;

/**
 * A leaf archetype product of the SAFAD taxonomy. Incoming products are matched to an archetype, whose stored
 * impacts feed the sustainability assessment. Identified externally by its {@code agb_code} (Agribalyse).
 */
@Entity
@Table(name = "TH_ARCHETYPE_PRODUCT")
public class ArchetypeProductEntity {
	@Id
	@Column(name = "id")
	private UUID id;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "category_id")
	private ArchetypeCategoryEntity category;

	@Column(name = "name")
	private String name;

	@Column(name = "agb_code")
	private String agbCode;

	@Embedded
	private EnvironmentalImpact environmentalImpact;

	@Embedded
	private SocialImpact socialImpact;

	@Embedded
	private AnimalWelfareImpact animalWelfareImpact;

	@Column(name = "nutri_score")
	private String nutriScore;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public ArchetypeCategoryEntity getCategory() {
		return category;
	}

	public void setCategory(ArchetypeCategoryEntity category) {
		this.category = category;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getAgbCode() {
		return agbCode;
	}

	public void setAgbCode(String agbCode) {
		this.agbCode = agbCode;
	}

	public EnvironmentalImpact getEnvironmentalImpact() {
		return environmentalImpact;
	}

	public void setEnvironmentalImpact(EnvironmentalImpact environmentalImpact) {
		this.environmentalImpact = environmentalImpact;
	}

	public SocialImpact getSocialImpact() {
		return socialImpact;
	}

	public void setSocialImpact(SocialImpact socialImpact) {
		this.socialImpact = socialImpact;
	}

	public AnimalWelfareImpact getAnimalWelfareImpact() {
		return animalWelfareImpact;
	}

	public void setAnimalWelfareImpact(AnimalWelfareImpact animalWelfareImpact) {
		this.animalWelfareImpact = animalWelfareImpact;
	}

	public String getNutriScore() {
		return nutriScore;
	}

	public void setNutriScore(String nutriScore) {
		this.nutriScore = nutriScore;
	}
}
