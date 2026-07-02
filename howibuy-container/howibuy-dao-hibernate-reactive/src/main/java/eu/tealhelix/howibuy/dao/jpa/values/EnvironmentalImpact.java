package eu.tealhelix.howibuy.dao.jpa.values;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * The 16 characterized environmental impacts of an archetype product, as provided by the WP3 database.
 * The single environmental scores are computed by the application, not stored.
 */
@Embeddable
public class EnvironmentalImpact {
	@Column(name = "e_climate_change")
	private double climateChange;

	@Column(name = "e_ozon_depletion")
	private double ozonDepletion;

	@Column(name = "e_ionizing_radiation")
	private double ionizingRadiation;

	@Column(name = "e_ozon_formation")
	private double ozonFormation;

	@Column(name = "e_particulate_matter")
	private double particulateMatter;

	@Column(name = "e_non_carcinogenic_toxicity")
	private double nonCarcinogenicToxicity;

	@Column(name = "e_carcinogenic_toxicity")
	private double carcinogenicToxicity;

	@Column(name = "e_land_water_acidification")
	private double landWaterAcidification;

	@Column(name = "e_freshwater_eutrophication")
	private double freshwaterEutrophication;

	@Column(name = "e_marine_eutrophication")
	private double marineEutrophication;

	@Column(name = "e_terrestrial_eutrophication")
	private double terrestrialEutrophication;

	@Column(name = "e_freshwater_ecotoxicity")
	private double freshwaterEcotoxicity;

	@Column(name = "e_land_use")
	private double landUse;

	@Column(name = "e_water_use")
	private double waterUse;

	@Column(name = "e_energy_use")
	private double energyUse;

	@Column(name = "e_mineral_use")
	private double mineralUse;

	public double getClimateChange() {
		return climateChange;
	}

	public double getOzonDepletion() {
		return ozonDepletion;
	}

	public double getIonizingRadiation() {
		return ionizingRadiation;
	}

	public double getOzonFormation() {
		return ozonFormation;
	}

	public double getParticulateMatter() {
		return particulateMatter;
	}

	public double getNonCarcinogenicToxicity() {
		return nonCarcinogenicToxicity;
	}

	public double getCarcinogenicToxicity() {
		return carcinogenicToxicity;
	}

	public double getLandWaterAcidification() {
		return landWaterAcidification;
	}

	public double getFreshwaterEutrophication() {
		return freshwaterEutrophication;
	}

	public double getMarineEutrophication() {
		return marineEutrophication;
	}

	public double getTerrestrialEutrophication() {
		return terrestrialEutrophication;
	}

	public double getFreshwaterEcotoxicity() {
		return freshwaterEcotoxicity;
	}

	public double getLandUse() {
		return landUse;
	}

	public double getWaterUse() {
		return waterUse;
	}

	public double getEnergyUse() {
		return energyUse;
	}

	public double getMineralUse() {
		return mineralUse;
	}
}
