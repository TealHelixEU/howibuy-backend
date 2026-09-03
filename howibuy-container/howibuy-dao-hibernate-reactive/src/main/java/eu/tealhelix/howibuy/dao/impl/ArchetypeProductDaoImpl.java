package eu.tealhelix.howibuy.dao.impl;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToDoubleFunction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Tuple;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.howibuy.dao.ArchetypeProductDao;
import eu.tealhelix.howibuy.dao.jpa.ArchetypeCategoryEntity_;
import eu.tealhelix.howibuy.dao.jpa.ArchetypeProductEntity;
import eu.tealhelix.howibuy.dao.jpa.ArchetypeProductEntity_;
import eu.tealhelix.howibuy.services.model.ArchetypeProduct;
import eu.tealhelix.howibuy.services.model.ArchetypeProductImpacts;
import eu.tealhelix.howibuy.services.model.ImmutableArchetypeProduct;
import eu.tealhelix.howibuy.services.model.ImmutableArchetypeProductImpacts;
import eu.tealhelix.howibuy.v1.types.SustainabilityIndicator;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class ArchetypeProductDaoImpl implements ArchetypeProductDao {
	/**
	 * Where each indicator's measured value is stored. The columns themselves are named on the embeddable that holds
	 * them; this says which indicator of the scoring method each one is.
	 */
	private static final Map<SustainabilityIndicator, ToDoubleFunction<ArchetypeProductEntity>> MEASUREMENTS = measurements();

	@Override
	public Uni<List<ArchetypeProduct>> retrieveProductsInCategory(ReactivePersistenceContext em, UUID categoryId) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createTupleQuery();
		var root = q.from(ArchetypeProductEntity.class);
		q.select(cb.tuple(root.get(ArchetypeProductEntity_.id), root.get(ArchetypeProductEntity_.name)))
				.where(cb.equal(root.get(ArchetypeProductEntity_.category).get(ArchetypeCategoryEntity_.id), categoryId))
				.orderBy(cb.asc(root.get(ArchetypeProductEntity_.name)), cb.asc(root.get(ArchetypeProductEntity_.id)));
		return em.createQuery(q).getResultList().map(ArchetypeProductDaoImpl::toArchetypeProducts);
	}

	@Override
	public Uni<List<ArchetypeProductImpacts>> retrieveAllWithImpacts(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createTupleQuery();
		var root = q.from(ArchetypeProductEntity.class);
		var l3category = root.join(ArchetypeProductEntity_.category);
		var l2categoryId = l3category.get(ArchetypeCategoryEntity_.parent).get(ArchetypeCategoryEntity_.id);
		q.select(cb.tuple(root, l2categoryId))
				.orderBy(cb.asc(root.get(ArchetypeProductEntity_.agbCode)));
		return em.createQuery(q).getResultList().map(ArchetypeProductDaoImpl::toArchetypeProductImpacts);
	}

	private static List<ArchetypeProductImpacts> toArchetypeProductImpacts(List<Tuple> tuples) {
		return tuples.stream().map(ArchetypeProductDaoImpl::toArchetypeProductImpacts).toList();
	}

	private static ArchetypeProductImpacts toArchetypeProductImpacts(Tuple tuple) {
		var product = tuple.get(0, ArchetypeProductEntity.class);
		return ImmutableArchetypeProductImpacts.builder()
				.id(product.getId())
				.name(product.getName())
				.agbCode(product.getAgbCode())
				.l2CategoryId(tuple.get(1, UUID.class))
				.indicatorValues(indicatorValues(product))
				.nutriScore(product.getNutriScore())
				.build();
	}

	private static Map<SustainabilityIndicator, Double> indicatorValues(ArchetypeProductEntity product) {
		var values = new EnumMap<SustainabilityIndicator, Double>(SustainabilityIndicator.class);
		MEASUREMENTS.forEach((indicator, measurement) -> values.put(indicator, measurement.applyAsDouble(product)));
		return values;
	}

	private static Map<SustainabilityIndicator, ToDoubleFunction<ArchetypeProductEntity>> measurements() {
		var measurements = new EnumMap<SustainabilityIndicator, ToDoubleFunction<ArchetypeProductEntity>>(SustainabilityIndicator.class);
		measurements.put(SustainabilityIndicator.CLIMATE_CHANGE, p -> p.getEnvironmentalImpact().getClimateChange());
		measurements.put(SustainabilityIndicator.OZONE_DEPLETION, p -> p.getEnvironmentalImpact().getOzoneDepletion());
		measurements.put(SustainabilityIndicator.IONIZING_RADIATION, p -> p.getEnvironmentalImpact().getIonizingRadiation());
		measurements.put(SustainabilityIndicator.OZONE_FORMATION, p -> p.getEnvironmentalImpact().getOzoneFormation());
		measurements.put(SustainabilityIndicator.PARTICULATE_MATTER, p -> p.getEnvironmentalImpact().getParticulateMatter());
		measurements.put(SustainabilityIndicator.NON_CARCINOGENIC_TOXICITY, p -> p.getEnvironmentalImpact().getNonCarcinogenicToxicity());
		measurements.put(SustainabilityIndicator.CARCINOGENIC_TOXICITY, p -> p.getEnvironmentalImpact().getCarcinogenicToxicity());
		measurements.put(SustainabilityIndicator.LAND_WATER_ACIDIFICATION, p -> p.getEnvironmentalImpact().getLandWaterAcidification());
		measurements.put(SustainabilityIndicator.FRESHWATER_EUTROPHICATION, p -> p.getEnvironmentalImpact().getFreshwaterEutrophication());
		measurements.put(SustainabilityIndicator.MARINE_EUTROPHICATION, p -> p.getEnvironmentalImpact().getMarineEutrophication());
		measurements.put(SustainabilityIndicator.TERRESTRIAL_EUTROPHICATION, p -> p.getEnvironmentalImpact().getTerrestrialEutrophication());
		measurements.put(SustainabilityIndicator.FRESHWATER_ECOTOXICITY, p -> p.getEnvironmentalImpact().getFreshwaterEcotoxicity());
		measurements.put(SustainabilityIndicator.LAND_USE, p -> p.getEnvironmentalImpact().getLandUse());
		measurements.put(SustainabilityIndicator.WATER_USE, p -> p.getEnvironmentalImpact().getWaterUse());
		measurements.put(SustainabilityIndicator.ENERGY_USE, p -> p.getEnvironmentalImpact().getEnergyUse());
		measurements.put(SustainabilityIndicator.MINERAL_USE, p -> p.getEnvironmentalImpact().getMineralUse());
		measurements.put(SustainabilityIndicator.ANIMAL_WELFARE_INDEX, p -> p.getAnimalWelfareImpact().getIndex());
		measurements.put(SustainabilityIndicator.ANTIBIOTIC_INDEX, p -> p.getAnimalWelfareImpact().getAntibioIndex());
		measurements.put(SustainabilityIndicator.CHILD_LABOUR, p -> p.getSocialImpact().getChildLabour());
		measurements.put(SustainabilityIndicator.FORCED_LABOUR, p -> p.getSocialImpact().getForcedLabour());
		measurements.put(SustainabilityIndicator.FAIR_SALARY, p -> p.getSocialImpact().getFairSalary());
		measurements.put(SustainabilityIndicator.WORKING_TIME, p -> p.getSocialImpact().getWorkingTime());
		measurements.put(SustainabilityIndicator.DISCRIMINATION, p -> p.getSocialImpact().getDiscrimination());
		measurements.put(SustainabilityIndicator.HEALTH_SAFETY_WORKERS, p -> p.getSocialImpact().getHealthSafetyWorkers());
		measurements.put(SustainabilityIndicator.SOCIAL_BENEFITS_LEGAL_ISSUES, p -> p.getSocialImpact().getSocialBenefitsLegalIssues());
		measurements.put(SustainabilityIndicator.WORKERS_RIGHTS, p -> p.getSocialImpact().getWorkersRights());
		measurements.put(SustainabilityIndicator.FAIR_COMPETITION, p -> p.getSocialImpact().getFairCompetition());
		measurements.put(SustainabilityIndicator.CORRUPTION, p -> p.getSocialImpact().getCorruption());
		measurements.put(SustainabilityIndicator.CONTRIBUTION_ECON_DEV, p -> p.getSocialImpact().getContributionEconDev());
		measurements.put(SustainabilityIndicator.ILLITERACY, p -> p.getSocialImpact().getIlliteracy());
		measurements.put(SustainabilityIndicator.HEALTH_SAFETY_SOCIETY, p -> p.getSocialImpact().getHealthSafetySociety());
		measurements.put(SustainabilityIndicator.INDIGENOUS_RIGHTS, p -> p.getSocialImpact().getIndigenousRights());
		return Map.copyOf(measurements);
	}

	private static List<ArchetypeProduct> toArchetypeProducts(List<Tuple> tuples) {
		return tuples.stream().map(ArchetypeProductDaoImpl::toArchetypeProduct).toList();
	}

	private static ArchetypeProduct toArchetypeProduct(Tuple tuple) {
		return ImmutableArchetypeProduct.builder()
				.id(tuple.get(0, UUID.class))
				.name(tuple.get(1, String.class))
				.build();
	}
}
