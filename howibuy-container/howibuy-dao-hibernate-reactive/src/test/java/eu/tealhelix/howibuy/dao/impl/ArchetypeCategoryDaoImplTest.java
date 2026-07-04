package eu.tealhelix.howibuy.dao.impl;

import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import eu.tealhelix.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.tealhelix.common.test.jpa.HibernateReactiveExtension;
import eu.tealhelix.common.test.liquibase.LiquibaseExtension;
import eu.tealhelix.howibuy.dao.jpa.ArchetypeCategoryEntity;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.hibernate.reactive.mutiny.Mutiny;
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

	@Container
	private static final PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGRES_IMAGE);

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final LiquibaseExtension liquibaseExtension =
			LiquibaseExtension.withContexts(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword(), "test");

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final HibernateReactiveExtension hibernateReactiveExtension =
			new HibernateReactiveExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword());

	@Test
	void retrievesOnlyL1CategoryNames(Mutiny.SessionFactory sessionFactory) {
		var sut = new ArchetypeCategoryDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var beverages = category(L1_BEVERAGES, (short) 1, null, "Beverages");
		var dairy = category(L1_DAIRY, (short) 1, null, "Dairy");
		var juices = category(L2_JUICES, (short) 2, beverages, "Juices");
		var orangeJuice = category(L3_ORANGE_JUICE, (short) 3, juices, "Orange juice");
		factory.withTransaction(tx -> tx.persistAll(beverages, dairy, juices, orangeJuice))
				.await().atMost(WAIT);

		var names = factory.withoutTransaction(sut::retrieveL1CategoryNames)
				.subscribe().withSubscriber(UniAssertSubscriber.create())
				.awaitItem(WAIT).getItem();

		assertEquals(2, names.size(), "exactly the two L1 categories, no duplicates");
		assertEquals(Set.of("Beverages", "Dairy"), Set.copyOf(names), "L1 names only; L2/L3 excluded");
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
