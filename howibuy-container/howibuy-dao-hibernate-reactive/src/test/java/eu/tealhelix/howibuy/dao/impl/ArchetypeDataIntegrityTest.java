package eu.tealhelix.howibuy.dao.impl;

import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static java.util.stream.Collectors.toSet;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import eu.tealhelix.howibuy.dao.jpa.ArchetypeCategoryEntity;
import eu.tealhelix.howibuy.dao.jpa.ArchetypeCategoryEntity_;
import eu.tealhelix.howibuy.dao.jpa.ArchetypeProductEntity;
import eu.tealhelix.howibuy.dao.jpa.ArchetypeProductEntity_;
import eu.tealhelix.howibuy.dao.jpa.ArchetypeSubstitutabilityEntity;
import eu.tealhelix.howibuy.dao.jpa.ArchetypeSubstitutabilityEntity_;
import eu.tealhelix.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.tealhelix.common.test.jpa.HibernateReactiveExtension;
import eu.tealhelix.common.test.liquibase.LiquibaseExtension;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Verifies that the real WP3 dataset (the {@code appdata} Liquibase context) imports correctly: the whole taxonomy
 * is present, a known archetype resolves to its full category path with the expected impacts, the products hold the
 * values of WP3's latest database, and the substitutability matrix agrees with the taxonomy it refers to.
 */
@Testcontainers
public class ArchetypeDataIntegrityTest {
	private static final Duration WAIT = Duration.ofSeconds(300);

	private static final UUID SAMPLE_PRODUCT_ID = UUID.fromString("6e5b2400-d9e5-53e7-a890-86a918ff6583");
	private static final UUID SAMPLE_L3_CATEGORY_ID = UUID.fromString("da753992-7ecf-5e04-9ae6-8214aed2f7c4");

	@Container
	private static final PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGRES_IMAGE);

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final LiquibaseExtension liquibaseExtension =
			new LiquibaseExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword(), "howibuy.db.changelog.xml", "appdata");

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final HibernateReactiveExtension hibernateReactiveExtension =
			new HibernateReactiveExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword());

	@Test
	void loadsTheWholeTaxonomy(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		assertEquals(20L, countCategories(factory, (short) 1), "L1 categories");
		assertEquals(124L, countCategories(factory, (short) 2), "L2 categories");
		assertEquals(685L, countCategories(factory, (short) 3), "L3 categories");
		assertEquals(2451L, countProducts(factory), "archetype products");
	}

	@Test
	void loadsTheSampleArchetypeWithItsFullPath(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var product = findProductByAgbCode(factory, "1019");
		assertEquals(SAMPLE_PRODUCT_ID, product.getId());
		assertEquals("Champagne kir (Cocktail of champagne with red fruit liqueur)", product.getName());
		assertEquals("Nutriscore_E", product.getNutriScore());
		assertEquals(1.56, product.getEnvironmentalImpact().getClimateChange(), 0.0);

		var l3Id = categoryIdOfProduct(factory, SAMPLE_PRODUCT_ID);
		assertEquals(SAMPLE_L3_CATEGORY_ID, l3Id);

		var l3 = findCategory(factory, l3Id);
		assertEquals((short) 3, l3.getLevel());
		assertEquals("Cocktail drink", l3.getName());

		var l2 = findCategory(factory, parentIdOf(factory, l3Id));
		assertEquals((short) 2, l2.getLevel());
		assertEquals("Alcoholic mixed drinks", l2.getName());

		var l1 = findCategory(factory, parentIdOf(factory, l2.getId()));
		assertEquals((short) 1, l1.getLevel());
		assertEquals("Alcoholic beverages", l1.getName());
	}

	/**
	 * The products were first loaded from WP3's database of 2026-05-27 and then updated from that of 2026-10-27. Each
	 * sample is a value the update changed: the alcoholic drinks gained a Nutri-Score, the Nutri-Score points came in
	 * (the waters' among them, the healthiest of all), Animal Welfare values were filled in, and values once rounded to
	 * two decimals come at full precision.
	 */
	@Test
	void holdsTheValuesOfTheWp3DatabaseOf20261027(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var kir = findProductByAgbCode(factory, "1019");
		assertEquals(35, kir.getNutriScorePoints(), "points of the Champagne kir");
		assertEquals(9.44E-08, kir.getEnvironmentalImpact().getOzoneDepletion(), 0.0, "ozone depletion of the Champagne kir");

		assertEquals(-14, findProductByAgbCode(factory, "18430").getNutriScorePoints(), "points of bottled water");
		assertEquals(-14, findProductByAgbCode(factory, "18066").getNutriScorePoints(), "points of tap water");

		var rabbitLiver = findProductByAgbCode(factory, "40110");
		assertEquals(143.5141596, rabbitLiver.getAnimalWelfareImpact().getIndex(), 0.0, "animal welfare index of raw rabbit liver");
		assertEquals(955.5686911, rabbitLiver.getAnimalWelfareImpact().getAntibioIndex(), 0.0, "antibiotic index of raw rabbit liver");
	}

	@Test
	void aProductOutsideTheNutriScoreSchemeHasThePlaceholderPoints(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var babyBiscuit = findProductByAgbCode(factory, "24689");
		assertEquals("0", babyBiscuit.getNutriScore(), "the label that says the biscuit for babies is outside the scheme");
		assertEquals(0, babyBiscuit.getNutriScorePoints(), "WP3's placeholder points");
	}

	/**
	 * The constraint waits for the points to be loaded, so it is checked where they have been.
	 */
	@Test
	void requiresTheNutriScorePointsOfEveryProduct() throws SQLException {
		try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
				var statement = connection.prepareStatement(
						"SELECT is_nullable FROM information_schema.columns WHERE table_name = 'th_archetype_product' AND column_name = 'nutri_score_points'");
				var result = statement.executeQuery()) {
			result.next();
			assertEquals("NO", result.getString(1), "nutri_score_points is nullable");
		}
	}

	@Test
	void loadsTheWholeSubstitutabilityMatrix(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		assertEquals(2634, substitutablePairs(factory).size(), "substitutable pairs");
	}

	@Test
	void everySubstitutabilityDegreeIsWithinTheAllowedRange(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var degrees = substitutablePairs(factory).stream().map(SubstitutablePair::degree).distinct().sorted().toList();
		assertEquals(List.of((short) 1, (short) 2, (short) 3, (short) 4, (short) 5), degrees, "distinct degrees");
	}

	@Test
	void everyCategoryIsSubstitutableForItself(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var selfPairs = substitutablePairs(factory).stream().filter(pair -> pair.from().equals(pair.to())).toList();
		assertEquals(124, selfPairs.size(), "self-substitutable categories");
		assertTrue(selfPairs.stream().allMatch(pair -> pair.degree() == 5), "the diagonal is the strongest degree");
	}

	/**
	 * The matrix and the taxonomy must describe the same 124 L2 categories. A category the matrix never mentions can
	 * never be assessed, and a category the taxonomy does not have would be a dangling reference. Checking both ends
	 * catches the two datasets drifting apart when either is regenerated.
	 */
	@Test
	void theMatrixAndTheTaxonomyAgreeOnEveryL2Category(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var taxonomy = l2CategoryIds(factory);
		var pairs = substitutablePairs(factory);

		assertEquals(124, taxonomy.size(), "L2 categories in the taxonomy");
		assertEquals(taxonomy, pairs.stream().map(SubstitutablePair::from).collect(toSet()), "categories the matrix can substitute with");
		assertEquals(taxonomy, pairs.stream().map(SubstitutablePair::to).collect(toSet()), "categories the matrix can substitute for");
	}

	private record SubstitutablePair(UUID from, UUID to, short degree) {}

	private static List<SubstitutablePair> substitutablePairs(ReactivePersistenceContextFactoryImpl factory) {
		return factory.withoutTransaction(em -> {
			var cb = em.getCriteriaBuilder();
			var q = cb.createTupleQuery();
			var root = q.from(ArchetypeSubstitutabilityEntity.class);
			q.select(cb.tuple(
					root.get(ArchetypeSubstitutabilityEntity_.fromCategory).get(ArchetypeCategoryEntity_.id),
					root.get(ArchetypeSubstitutabilityEntity_.toCategory).get(ArchetypeCategoryEntity_.id),
					root.get(ArchetypeSubstitutabilityEntity_.degree)));
			return em.createQuery(q).getResultList();
		}).await().atMost(WAIT).stream()
				.map(t -> new SubstitutablePair(t.get(0, UUID.class), t.get(1, UUID.class), t.get(2, Short.class)))
				.toList();
	}

	private static Set<UUID> l2CategoryIds(ReactivePersistenceContextFactoryImpl factory) {
		return Set.copyOf(factory.withoutTransaction(em -> {
			var cb = em.getCriteriaBuilder();
			var q = cb.createQuery(UUID.class);
			var root = q.from(ArchetypeCategoryEntity.class);
			q.select(root.get(ArchetypeCategoryEntity_.id)).where(cb.equal(root.get(ArchetypeCategoryEntity_.level), (short) 2));
			return em.createQuery(q).getResultList();
		}).await().atMost(WAIT));
	}

	private static ArchetypeProductEntity findProductByAgbCode(ReactivePersistenceContextFactoryImpl factory, String agbCode) {
		return factory.withoutTransaction(em -> {
			var cb = em.getCriteriaBuilder();
			var q = cb.createQuery(ArchetypeProductEntity.class);
			var root = q.from(ArchetypeProductEntity.class);
			q.where(cb.equal(root.get(ArchetypeProductEntity_.agbCode), agbCode));
			return em.createQuery(q).getSingleResult();
		}).await().atMost(WAIT);
	}

	private static long countCategories(ReactivePersistenceContextFactoryImpl factory, short level) {
		return factory.withoutTransaction(em -> {
			var cb = em.getCriteriaBuilder();
			var q = cb.createQuery(Long.class);
			var root = q.from(ArchetypeCategoryEntity.class);
			q.select(cb.count(root)).where(cb.equal(root.get(ArchetypeCategoryEntity_.level), level));
			return em.createQuery(q).getSingleResult();
		}).await().atMost(WAIT);
	}

	private static long countProducts(ReactivePersistenceContextFactoryImpl factory) {
		return factory.withoutTransaction(em -> {
			var cb = em.getCriteriaBuilder();
			var q = cb.createQuery(Long.class);
			var root = q.from(ArchetypeProductEntity.class);
			q.select(cb.count(root));
			return em.createQuery(q).getSingleResult();
		}).await().atMost(WAIT);
	}

	private static UUID categoryIdOfProduct(ReactivePersistenceContextFactoryImpl factory, UUID productId) {
		return factory.withoutTransaction(em -> {
			var cb = em.getCriteriaBuilder();
			var q = cb.createQuery(UUID.class);
			var root = q.from(ArchetypeProductEntity.class);
			q.select(root.get(ArchetypeProductEntity_.category).get(ArchetypeCategoryEntity_.id))
					.where(cb.equal(root.get(ArchetypeProductEntity_.id), productId));
			return em.createQuery(q).getSingleResult();
		}).await().atMost(WAIT);
	}

	private static UUID parentIdOf(ReactivePersistenceContextFactoryImpl factory, UUID categoryId) {
		return factory.withoutTransaction(em -> {
			var cb = em.getCriteriaBuilder();
			var q = cb.createQuery(UUID.class);
			var root = q.from(ArchetypeCategoryEntity.class);
			q.select(root.get(ArchetypeCategoryEntity_.parent).get(ArchetypeCategoryEntity_.id))
					.where(cb.equal(root.get(ArchetypeCategoryEntity_.id), categoryId));
			return em.createQuery(q).getSingleResult();
		}).await().atMost(WAIT);
	}

	private static ArchetypeCategoryEntity findCategory(ReactivePersistenceContextFactoryImpl factory, UUID id) {
		return factory.withoutTransaction(em -> em.find(ArchetypeCategoryEntity.class, id)).await().atMost(WAIT);
	}
}
