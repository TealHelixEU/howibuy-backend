package eu.tealhelix.howibuy.dao.impl;

import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import eu.tealhelix.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.tealhelix.common.test.jpa.HibernateReactiveExtension;
import eu.tealhelix.common.test.liquibase.LiquibaseExtension;
import eu.tealhelix.howibuy.dao.jpa.ArchetypeCategoryEntity;
import eu.tealhelix.howibuy.services.model.ArchetypeCategory;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
public class ArchetypeCategoryDaoImplTest {
	private static final Duration WAIT = Duration.ofSeconds(300);

	private static final UUID L1_BEVERAGES = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID L1_DAIRY = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final UUID L2_JUICES = UUID.fromString("00000000-0000-0000-0000-000000000003");
	private static final UUID L3_ORANGE_JUICE = UUID.fromString("00000000-0000-0000-0000-000000000004");
	private static final UUID L2_OTHER_BEVERAGES = UUID.fromString("00000000-0000-0000-0000-000000000005");
	private static final UUID L2_OTHER_DAIRY = UUID.fromString("00000000-0000-0000-0000-000000000006");

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
	 * Seed the taxonomy once; both tests only read it. "Other" appears as an L2 under two different L1 parents to
	 * exercise the parent-scoped (not name-based) subcategory lookup.
	 * <pre>
	 * Beverages (L1)          Dairy (L1)
	 *   Juices (L2)             Other (L2)
	 *     Orange juice (L3)
	 *   Other (L2)
	 * </pre>
	 */
	@BeforeAll
	static void seedTaxonomy(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var beverages = category(L1_BEVERAGES, (short) 1, null, "Beverages");
		var dairy = category(L1_DAIRY, (short) 1, null, "Dairy");
		var juices = category(L2_JUICES, (short) 2, beverages, "Juices");
		var orangeJuice = category(L3_ORANGE_JUICE, (short) 3, juices, "Orange juice");
		var otherBeverages = category(L2_OTHER_BEVERAGES, (short) 2, beverages, "Other");
		var otherDairy = category(L2_OTHER_DAIRY, (short) 2, dairy, "Other");
		// Persist reverse-alphabetically within each sibling set (Dairy before Beverages; Other before Juices) so a
		// missing ORDER BY would surface as insertion-ordered results, letting the ordering tests catch it.
		factory.withTransaction(tx -> tx.persistAll(dairy, beverages, otherBeverages, juices, orangeJuice, otherDairy))
				.await().atMost(WAIT);
	}

	@Test
	void retrievesOnlyL1Categories(Mutiny.SessionFactory sessionFactory) {
		var sut = new ArchetypeCategoryDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var l1categories = factory.withoutTransaction(sut::retrieveL1Categories)
				.subscribe().withSubscriber(UniAssertSubscriber.create())
				.awaitItem(WAIT).getItem();

		assertEquals(
				Map.of(L1_BEVERAGES, "Beverages", L1_DAIRY, "Dairy"),
				byIdAndName(l1categories),
				"exactly the two L1 categories (id + name); L2/L3 excluded");
	}

	@Test
	void retrievesDirectChildrenOfGivenParentOnly(Mutiny.SessionFactory sessionFactory) {
		var sut = new ArchetypeCategoryDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var subcategories = factory.withoutTransaction(em -> sut.retrieveSubcategories(em, L1_BEVERAGES))
				.subscribe().withSubscriber(UniAssertSubscriber.create())
				.awaitItem(WAIT).getItem();

		assertEquals(
				Map.of(L2_JUICES, "Juices", L2_OTHER_BEVERAGES, "Other"),
				byIdAndName(subcategories),
				"only Beverages' direct children: the identically-named 'Other' under Dairy and the L3 grandchild are excluded");
	}

	@Test
	void retrievesL1CategoriesOrderedByName(Mutiny.SessionFactory sessionFactory) {
		var sut = new ArchetypeCategoryDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var l1categories = factory.withoutTransaction(sut::retrieveL1Categories)
				.subscribe().withSubscriber(UniAssertSubscriber.create())
				.awaitItem(WAIT).getItem();

		assertEquals(
				List.of("Beverages", "Dairy"),
				names(l1categories),
				"L1 candidates are ordered by name so the classifier prompt is deterministic (seeded reverse-alphabetically)");
	}

	@Test
	void retrievesSubcategoriesOrderedByName(Mutiny.SessionFactory sessionFactory) {
		var sut = new ArchetypeCategoryDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var subcategories = factory.withoutTransaction(em -> sut.retrieveSubcategories(em, L1_BEVERAGES))
				.subscribe().withSubscriber(UniAssertSubscriber.create())
				.awaitItem(WAIT).getItem();

		assertEquals(
				List.of("Juices", "Other"),
				names(subcategories),
				"subcategory candidates are ordered by name so the classifier prompt is deterministic (seeded reverse-alphabetically)");
	}

	private static Map<UUID, String> byIdAndName(List<ArchetypeCategory> categories) {
		return categories.stream().collect(toMap(ArchetypeCategory::getId, ArchetypeCategory::getName));
	}

	private static List<String> names(List<ArchetypeCategory> categories) {
		return categories.stream().map(ArchetypeCategory::getName).toList();
	}

	private static ArchetypeCategoryEntity category(UUID id, short level, ArchetypeCategoryEntity parent, String name) {
		var category = new ArchetypeCategoryEntity();
		category.setId(id);
		category.setLevel(level);
		category.setParent(parent);
		category.setName(name);
		return category;
	}
}
