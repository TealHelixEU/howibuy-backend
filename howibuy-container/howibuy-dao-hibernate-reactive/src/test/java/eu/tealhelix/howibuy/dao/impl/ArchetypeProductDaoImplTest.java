package eu.tealhelix.howibuy.dao.impl;

import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.ANIMAL_WELFARE_INDEX;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.ANTIBIOTIC_INDEX;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.CARCINOGENIC_TOXICITY;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.CHILD_LABOUR;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.CLIMATE_CHANGE;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.CONTRIBUTION_ECON_DEV;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.CORRUPTION;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.DISCRIMINATION;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.ENERGY_USE;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.FAIR_COMPETITION;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.FAIR_SALARY;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.FORCED_LABOUR;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.FRESHWATER_ECOTOXICITY;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.FRESHWATER_EUTROPHICATION;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.HEALTH_SAFETY_SOCIETY;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.HEALTH_SAFETY_WORKERS;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.ILLITERACY;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.INDIGENOUS_RIGHTS;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.IONIZING_RADIATION;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.LAND_USE;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.LAND_WATER_ACIDIFICATION;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.MARINE_EUTROPHICATION;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.MINERAL_USE;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.NON_CARCINOGENIC_TOXICITY;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.OZONE_DEPLETION;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.OZONE_FORMATION;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.PARTICULATE_MATTER;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.SOCIAL_BENEFITS_LEGAL_ISSUES;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.TERRESTRIAL_EUTROPHICATION;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.WATER_USE;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.WORKERS_RIGHTS;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.WORKING_TIME;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import eu.tealhelix.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.tealhelix.common.test.jpa.HibernateReactiveExtension;
import eu.tealhelix.common.test.liquibase.LiquibaseExtension;
import eu.tealhelix.howibuy.dao.jpa.ArchetypeCategoryEntity;
import eu.tealhelix.howibuy.dao.jpa.ArchetypeProductEntity;
import eu.tealhelix.howibuy.dao.jpa.values.AnimalWelfareImpact;
import eu.tealhelix.howibuy.dao.jpa.values.EnvironmentalImpact;
import eu.tealhelix.howibuy.dao.jpa.values.SocialImpact;
import eu.tealhelix.howibuy.services.model.ArchetypeProduct;
import eu.tealhelix.howibuy.services.model.ArchetypeProductImpacts;
import eu.tealhelix.howibuy.v1.types.SustainabilityIndicator;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
public class ArchetypeProductDaoImplTest {
	private static final Duration WAIT = Duration.ofSeconds(300);

	/** How the seed data spells "this product is outside the Nutri-Score scheme", as WP3 does. */
	private static final String UNSCOREABLE = "0";

	private static final UUID L1_BEVERAGES = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID L2_JUICES = UUID.fromString("00000000-0000-0000-0000-000000000011");
	private static final UUID L2_DAIRY = UUID.fromString("00000000-0000-0000-0000-000000000012");
	private static final UUID L3_JUICES = UUID.fromString("00000000-0000-0000-0000-000000000101");
	private static final UUID L3_MILKS = UUID.fromString("00000000-0000-0000-0000-000000000102");
	private static final UUID ORANGE_JUICE = UUID.fromString("00000000-0000-0000-0000-000000000201");
	private static final UUID GENERIC_JUICE = UUID.fromString("00000000-0000-0000-0000-000000000202");
	private static final UUID WHOLE_MILK = UUID.fromString("00000000-0000-0000-0000-000000000203");
	private static final UUID GENERIC_MILK = UUID.fromString("00000000-0000-0000-0000-000000000204");

	/**
	 * Every impact column of {@code TH_ARCHETYPE_PRODUCT} against the indicator it measures, written out here so the
	 * expected mapping is stated independently of the entity that implements it.
	 */
	private static final List<ImpactColumn> IMPACT_COLUMNS = List.of(
			new ImpactColumn("e_climate_change", CLIMATE_CHANGE),
			new ImpactColumn("e_ozone_depletion", OZONE_DEPLETION),
			new ImpactColumn("e_ionizing_radiation", IONIZING_RADIATION),
			new ImpactColumn("e_ozone_formation", OZONE_FORMATION),
			new ImpactColumn("e_particulate_matter", PARTICULATE_MATTER),
			new ImpactColumn("e_non_carcinogenic_toxicity", NON_CARCINOGENIC_TOXICITY),
			new ImpactColumn("e_carcinogenic_toxicity", CARCINOGENIC_TOXICITY),
			new ImpactColumn("e_land_water_acidification", LAND_WATER_ACIDIFICATION),
			new ImpactColumn("e_freshwater_eutrophication", FRESHWATER_EUTROPHICATION),
			new ImpactColumn("e_marine_eutrophication", MARINE_EUTROPHICATION),
			new ImpactColumn("e_terrestrial_eutrophication", TERRESTRIAL_EUTROPHICATION),
			new ImpactColumn("e_freshwater_ecotoxicity", FRESHWATER_ECOTOXICITY),
			new ImpactColumn("e_land_use", LAND_USE),
			new ImpactColumn("e_water_use", WATER_USE),
			new ImpactColumn("e_energy_use", ENERGY_USE),
			new ImpactColumn("e_mineral_use", MINERAL_USE),
			new ImpactColumn("aw_index", ANIMAL_WELFARE_INDEX),
			new ImpactColumn("aw_antibio_index", ANTIBIOTIC_INDEX),
			new ImpactColumn("s_child_labour", CHILD_LABOUR),
			new ImpactColumn("s_forced_labour", FORCED_LABOUR),
			new ImpactColumn("s_fair_salary", FAIR_SALARY),
			new ImpactColumn("s_working_time", WORKING_TIME),
			new ImpactColumn("s_discrimination", DISCRIMINATION),
			new ImpactColumn("s_health_safety_workers", HEALTH_SAFETY_WORKERS),
			new ImpactColumn("s_social_benefits_legal_issues", SOCIAL_BENEFITS_LEGAL_ISSUES),
			new ImpactColumn("s_workers_rights", WORKERS_RIGHTS),
			new ImpactColumn("s_fair_competition", FAIR_COMPETITION),
			new ImpactColumn("s_corruption", CORRUPTION),
			new ImpactColumn("s_contribution_econ_dev", CONTRIBUTION_ECON_DEV),
			new ImpactColumn("s_illiteracy", ILLITERACY),
			new ImpactColumn("s_health_safety_society", HEALTH_SAFETY_SOCIETY),
			new ImpactColumn("s_indigenous_rights", INDIGENOUS_RIGHTS));

	private record ImpactColumn(String column, SustainabilityIndicator indicator) {
	}

	@Container
	private static final PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGRES_IMAGE);

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final LiquibaseExtension liquibaseExtension =
			new LiquibaseExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword(), "howibuy.db.changelog.xml", "test");

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final HibernateReactiveExtension hibernateReactiveExtension =
			new HibernateReactiveExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword());

	/**
	 * Seed two L3 categories under two L2 subcategories of one L1 category, each L3 holding archetype products;
	 * "Generic" appears under both to exercise the category-scoped (not name-based) product lookup.
	 * <pre>
	 * Beverages (L1)
	 *   Juices (L2) — Juices (L3)         Dairy (L2) — Milks (L3)
	 *                   Orange juice                    Whole milk
	 *                   Generic                         Generic
	 * </pre>
	 * The two L2 subcategories are what {@code retrieveAllWithImpacts} must report, and the two levels share a name so
	 * that reporting the L3 leaf by mistake cannot pass. The impacts are written afterwards over JDBC, naming the
	 * columns directly, so the entity's own column mapping is not both the subject and the yardstick of the test.
	 */
	@BeforeAll
	static void seedProducts(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var beverages = category(L1_BEVERAGES, (short) 1, null, "Beverages");
		var l2juices = category(L2_JUICES, (short) 2, beverages, "Juices");
		var l2dairy = category(L2_DAIRY, (short) 2, beverages, "Dairy");
		var juices = category(L3_JUICES, (short) 3, l2juices, "Juices");
		var milks = category(L3_MILKS, (short) 3, l2dairy, "Milks");
		var orangeJuice = product(ORANGE_JUICE, juices, "Orange juice", "agb-oj", "Nutriscore_B");
		var genericJuice = product(GENERIC_JUICE, juices, "Generic", "agb-gj", "Nutriscore_A");
		var wholeMilk = product(WHOLE_MILK, milks, "Whole milk", "agb-wm", "Nutriscore_C");
		var genericMilk = product(GENERIC_MILK, milks, "Generic", "agb-gm", UNSCOREABLE);
		factory.withTransaction(tx -> tx.persistAll(beverages, l2juices, l2dairy, juices, milks)
						.chain(() -> tx.persistAll(orangeJuice, genericJuice, wholeMilk, genericMilk)))
				.await().atMost(WAIT);
		writeImpacts(ORANGE_JUICE);
	}

	/**
	 * One distinct value per column, so that any two columns mapped to each other's indicator disagree with the
	 * expectation rather than cancelling out.
	 */
	private static void writeImpacts(UUID productId) {
		var assignments = IntStream.range(0, IMPACT_COLUMNS.size())
				.mapToObj(i -> IMPACT_COLUMNS.get(i).column() + " = " + valueOfColumn(i))
				.collect(joining(", "));
		try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
				var statement = connection.prepareStatement("UPDATE TH_ARCHETYPE_PRODUCT SET " + assignments + " WHERE id = ?")) {
			statement.setObject(1, productId);
			statement.executeUpdate();
		} catch (SQLException e) {
			throw new IllegalStateException("Could not write the archetype product impacts, id: " + productId, e);
		}
	}

	private static double valueOfColumn(int index) {
		return index + 1;
	}

	@Test
	void retrievesProductsOfGivenCategoryOnly(Mutiny.SessionFactory sessionFactory) {
		var sut = new ArchetypeProductDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var products = factory.withoutTransaction(em -> sut.retrieveProductsInCategory(em, L3_JUICES))
				.subscribe().withSubscriber(UniAssertSubscriber.create())
				.awaitItem(WAIT).getItem();

		assertEquals(
				Map.of(ORANGE_JUICE, "Orange juice", GENERIC_JUICE, "Generic"),
				byIdAndName(products),
				"only Juices' products: Milks' products, including the identically-named 'Generic', are excluded");
	}

	@Test
	void retrievesProductsOrderedByName(Mutiny.SessionFactory sessionFactory) {
		var sut = new ArchetypeProductDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var products = factory.withoutTransaction(em -> sut.retrieveProductsInCategory(em, L3_JUICES))
				.subscribe().withSubscriber(UniAssertSubscriber.create())
				.awaitItem(WAIT).getItem();

		assertEquals(
				List.of("Generic", "Orange juice"),
				names(products),
				"candidates are ordered by name so the classifier prompt is deterministic (seeded reverse-alphabetically)");
	}

	@Test
	void retrievesTheWholeCorpusWithEachProductsL2CategoryRatherThanItsL3Leaf(Mutiny.SessionFactory sessionFactory) {
		var corpus = retrieveCorpus(sessionFactory);

		assertEquals(
				Map.of(ORANGE_JUICE, L2_JUICES, GENERIC_JUICE, L2_JUICES, WHOLE_MILK, L2_DAIRY, GENERIC_MILK, L2_DAIRY),
				corpus.stream().collect(toMap(ArchetypeProductImpacts::getId, ArchetypeProductImpacts::getL2CategoryId)),
				"substitutability is decided at L2, so each product reports the parent of the leaf it hangs from");
	}

	@Test
	void retrievesTheNameAndAgbCodeOfEveryProductInTheCorpus(Mutiny.SessionFactory sessionFactory) {
		var corpus = retrieveCorpus(sessionFactory);

		assertEquals(
				Map.of(ORANGE_JUICE, "Orange juice/agb-oj", GENERIC_JUICE, "Generic/agb-gj",
						WHOLE_MILK, "Whole milk/agb-wm", GENERIC_MILK, "Generic/agb-gm"),
				corpus.stream().collect(toMap(
						ArchetypeProductImpacts::getId,
						product -> product.getName() + "/" + product.getAgbCode())),
				"the name is what a recommendation is shown by, the agb code what ties between equal scores break on");
	}

	@Test
	void mapsEveryImpactColumnToTheIndicatorItMeasures(Mutiny.SessionFactory sessionFactory) {
		var corpus = retrieveCorpus(sessionFactory);

		var expected = IntStream.range(0, IMPACT_COLUMNS.size()).boxed().collect(toMap(
				i -> IMPACT_COLUMNS.get(i).indicator(),
				ArchetypeProductDaoImplTest::valueOfColumn));
		assertEquals(expected, productOf(corpus, ORANGE_JUICE).getIndicatorValues(),
				"all 32 measured columns, each under its own indicator");
	}

	@Test
	void retrievesAProductOutsideTheNutriScoreSchemeAlongWithTheRest(Mutiny.SessionFactory sessionFactory) {
		var corpus = retrieveCorpus(sessionFactory);

		assertEquals(UNSCOREABLE, productOf(corpus, GENERIC_MILK).getNutriScore(),
				"a product with no health score is still part of the range the others are normalised against, "
						+ "so the corpus is loaded whole and thinned later");
		assertEquals("Nutriscore_B", productOf(corpus, ORANGE_JUICE).getNutriScore());
	}

	private static List<ArchetypeProductImpacts> retrieveCorpus(Mutiny.SessionFactory sessionFactory) {
		var sut = new ArchetypeProductDaoImpl();
		return new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withoutTransaction(sut::retrieveAllWithImpacts)
				.subscribe().withSubscriber(UniAssertSubscriber.create())
				.awaitItem(WAIT).getItem();
	}

	private static ArchetypeProductImpacts productOf(List<ArchetypeProductImpacts> corpus, UUID productId) {
		return corpus.stream()
				.filter(product -> product.getId().equals(productId))
				.findFirst()
				.orElseThrow(() -> new AssertionError("The corpus does not contain the product, id: " + productId));
	}

	private static Map<UUID, String> byIdAndName(List<ArchetypeProduct> products) {
		return products.stream().collect(toMap(ArchetypeProduct::getId, ArchetypeProduct::getName));
	}

	private static List<String> names(List<ArchetypeProduct> products) {
		return products.stream().map(ArchetypeProduct::getName).toList();
	}

	private static ArchetypeCategoryEntity category(UUID id, short level, ArchetypeCategoryEntity parent, String name) {
		var category = new ArchetypeCategoryEntity();
		category.setId(id);
		category.setLevel(level);
		category.setParent(parent);
		category.setName(name);
		return category;
	}

	private static ArchetypeProductEntity product(
			UUID id, ArchetypeCategoryEntity category, String name, String agbCode, String nutriScore) {
		var product = new ArchetypeProductEntity();
		product.setId(id);
		product.setCategory(category);
		product.setName(name);
		product.setAgbCode(agbCode);
		product.setEnvironmentalImpact(new EnvironmentalImpact());
		product.setSocialImpact(new SocialImpact());
		product.setAnimalWelfareImpact(new AnimalWelfareImpact());
		product.setNutriScore(nutriScore);
		return product;
	}
}
