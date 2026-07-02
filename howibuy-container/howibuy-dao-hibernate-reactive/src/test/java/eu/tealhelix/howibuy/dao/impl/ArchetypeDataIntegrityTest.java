package eu.tealhelix.howibuy.dao.impl;

import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.UUID;

import eu.tealhelix.howibuy.dao.jpa.ArchetypeCategoryEntity;
import eu.tealhelix.howibuy.dao.jpa.ArchetypeCategoryEntity_;
import eu.tealhelix.howibuy.dao.jpa.ArchetypeProductEntity;
import eu.tealhelix.howibuy.dao.jpa.ArchetypeProductEntity_;
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
 * is present and a known archetype resolves to its full category path with the expected impacts.
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
			LiquibaseExtension.withContexts(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword(), "appdata");

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
		assertEquals("0", product.getNutriScore());
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
