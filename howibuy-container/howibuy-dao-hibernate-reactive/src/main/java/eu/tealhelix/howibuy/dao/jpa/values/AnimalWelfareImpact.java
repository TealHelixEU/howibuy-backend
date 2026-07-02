package eu.tealhelix.howibuy.dao.jpa.values;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * The animal-welfare indicators of an archetype product, as provided by the WP3 database.
 * The single animal-welfare scores are computed by the application, not stored.
 */
@Embeddable
public class AnimalWelfareImpact {
	@Column(name = "aw_index")
	private double index;

	@Column(name = "aw_antibio_index")
	private double antibioIndex;

	public double getIndex() {
		return index;
	}

	public double getAntibioIndex() {
		return antibioIndex;
	}
}
