package eu.tealhelix.howibuy.dao.impl;

import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import eu.tealhelix.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.tealhelix.common.test.jpa.HibernateReactiveExtension;
import eu.tealhelix.common.test.liquibase.LiquibaseExtension;
import eu.tealhelix.howibuy.dao.jpa.ArchetypeCategoryEntity;
import eu.tealhelix.howibuy.dao.jpa.ArchetypeSubstitutabilityEntity;
import eu.tealhelix.howibuy.services.model.Substitutability;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
public class SubstitutabilityDaoImplTest {
	private static final Duration WAIT = Duration.ofSeconds(300);

	private static final UUID L1_BEVERAGES = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID L2_JUICES = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final UUID L2_SOFT_DRINKS = UUID.fromString("00000000-0000-0000-0000-000000000003");
	private static final UUID L2_WATER = UUID.fromString("00000000-0000-0000-0000-000000000004");

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
	 * Seed a matrix once; both tests only read it. It is deliberately asymmetric — juices substitute for water but not
	 * the other way round — so a query that swapped the two ends would be caught, and it omits water's diagonal so
	 * that a query inventing rows would be caught too.
	 * <pre>
	 * from \ to    juices  soft drinks  water
	 * juices          5         3         1
	 * soft drinks     3         5         -
	 * water           -         -         -
	 * </pre>
	 */
	@BeforeAll
	static void seedMatrix(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var beverages = category(L1_BEVERAGES, (short) 1, null, "Beverages");
		var juices = category(L2_JUICES, (short) 2, beverages, "Juices");
		var softDrinks = category(L2_SOFT_DRINKS, (short) 2, beverages, "Soft drinks");
		var water = category(L2_WATER, (short) 2, beverages, "Water");
		// Persist the pairs in reverse of the expected order so a missing ORDER BY would surface as insertion-ordered
		// results, letting the ordering test catch it.
		factory.withTransaction(tx -> tx.persistAll(beverages, juices, softDrinks, water)
						.chain(() -> tx.persistAll(
								substitutability(softDrinks, softDrinks, (short) 5),
								substitutability(softDrinks, juices, (short) 3),
								substitutability(juices, water, (short) 1),
								substitutability(juices, softDrinks, (short) 3),
								substitutability(juices, juices, (short) 5))))
				.await().atMost(WAIT);
	}

	@Test
	void retrievesEveryStoredPairWithBothEndsAndItsDegree(Mutiny.SessionFactory sessionFactory) {
		var sut = new SubstitutabilityDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var matrix = factory.withoutTransaction(sut::retrieveAll)
				.subscribe().withSubscriber(UniAssertSubscriber.create())
				.awaitItem(WAIT).getItem();

		assertEquals(
				Set.of(
						new Pair(L2_JUICES, L2_JUICES, (short) 5),
						new Pair(L2_JUICES, L2_SOFT_DRINKS, (short) 3),
						new Pair(L2_JUICES, L2_WATER, (short) 1),
						new Pair(L2_SOFT_DRINKS, L2_JUICES, (short) 3),
						new Pair(L2_SOFT_DRINKS, L2_SOFT_DRINKS, (short) 5)),
				Set.copyOf(pairs(matrix)),
				"every stored pair, keeping its direction and degree; the unstored water pairs stay absent");
	}

	@Test
	void retrievesPairsOrderedByFromThenToCategory(Mutiny.SessionFactory sessionFactory) {
		var sut = new SubstitutabilityDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var matrix = factory.withoutTransaction(sut::retrieveAll)
				.subscribe().withSubscriber(UniAssertSubscriber.create())
				.awaitItem(WAIT).getItem();

		assertEquals(
				List.of(
						new Pair(L2_JUICES, L2_JUICES, (short) 5),
						new Pair(L2_JUICES, L2_SOFT_DRINKS, (short) 3),
						new Pair(L2_JUICES, L2_WATER, (short) 1),
						new Pair(L2_SOFT_DRINKS, L2_JUICES, (short) 3),
						new Pair(L2_SOFT_DRINKS, L2_SOFT_DRINKS, (short) 5)),
				pairs(matrix),
				"the whole matrix is loaded once and indexed by the caller, so a stable order keeps that indexing "
						+ "reproducible (seeded in reverse)");
	}

	private record Pair(UUID from, UUID to, short degree) {
	}

	private static List<Pair> pairs(List<Substitutability> matrix) {
		return matrix.stream()
				.map(s -> new Pair(s.getFromCategoryId(), s.getToCategoryId(), s.getDegree()))
				.toList();
	}

	private static ArchetypeSubstitutabilityEntity substitutability(
			ArchetypeCategoryEntity from, ArchetypeCategoryEntity to, short degree) {
		var substitutability = new ArchetypeSubstitutabilityEntity();
		substitutability.setFromCategory(from);
		substitutability.setToCategory(to);
		substitutability.setDegree(degree);
		return substitutability;
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
