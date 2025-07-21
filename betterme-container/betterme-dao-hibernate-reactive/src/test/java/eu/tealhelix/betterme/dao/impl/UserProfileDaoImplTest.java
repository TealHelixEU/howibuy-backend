package eu.tealhelix.betterme.dao.impl;

import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;

import eu.tealhelix.betterme.dao.jpa.UserProfileEntity;
import eu.tealhelix.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.tealhelix.common.test.jpa.HibernateReactiveExtension;
import eu.tealhelix.common.test.liquibase.LiquibaseExtension;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Testcontainers
public class UserProfileDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	@Container
	private static final PostgreSQLContainer<?> postgres =
			new PostgreSQLContainer<>(POSTGRES_IMAGE);

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final LiquibaseExtension liquibaseExtension =
			new LiquibaseExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword());

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final HibernateReactiveExtension hibernateReactiveExtension =
			new HibernateReactiveExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword());

	@Test
	@Order(1)
	void testCreation(Mutiny.SessionFactory sessionFactory) {
		var sut = new UserProfileDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var returnedUser = factory.withTransaction(sut::createAutoUser).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertNotNull(returnedUser);
		assertNotNull(returnedUser.getId());
		var actualUser = factory.withTransaction(tx ->
				tx.find(UserProfileEntity.class, returnedUser.getId().asUuid())
		).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertNotNull(actualUser);
		assertNotNull(actualUser.getId());
		assertEquals(returnedUser.getId().asUuid(), actualUser.getId());
	}
}
