package eu.tealhelix.betterme.dao.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import jakarta.persistence.criteria.Root;

import eu.tealhelix.betterme.dao.jpa.ConsentEntity;
import eu.tealhelix.betterme.dao.jpa.ConsentEntity_;
import eu.tealhelix.betterme.dao.jpa.RetailerEntity;
import eu.tealhelix.betterme.dao.jpa.RetailerEntity_;
import eu.tealhelix.betterme.dao.jpa.UserProfileEntity;
import eu.tealhelix.betterme.dao.jpa.UserProfileEntity_;
import eu.tealhelix.betterme.dao.jpa.values.ConsentPK;
import eu.tealhelix.betterme.v1.types.impl.GenericRetailerId;
import eu.tealhelix.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.tealhelix.common.test.jpa.HibernateReactiveExtension;
import eu.tealhelix.common.test.liquibase.LiquibaseExtension;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Testcontainers
public class ConsentDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final UUID USER_ID = UUID.fromString("2e788895-0503-4777-a7bd-24e5d61db5b1");
	private static final UUID USER_EXTERNAL_ID = UUID.fromString("2e788895-0503-4777-a7bd-24e5d61d0000");
	private static final UUID RETAILER_ID = UUID.fromString("518cae6a-f2b2-4454-b74d-f2404feab2f5");

	@Container
	private static final PostgreSQLContainer<?> postgres =
			new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine").asCompatibleSubstituteFor("postgres"));

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
	void test(Mutiny.SessionFactory sessionFactory) {
		var sut = new ConsentDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		factory.withTransaction(tx -> {
			var retailer = new RetailerEntity();
			retailer.setId(RETAILER_ID);
			retailer.setName("Retailer 1");
			var user = new UserProfileEntity();
			user.setId(USER_ID);
			user.setExternalId(USER_EXTERNAL_ID.toString());
			return tx.persistAll(retailer, user);
		}).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var userId = new UserIdImpl(USER_ID.toString());
		var retailerId = new GenericRetailerId(RETAILER_ID.toString());
		Boolean hasConsentedInitially = factory.withTransaction(tx -> sut.hasConsentedToRetailer(tx, userId, retailerId))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertFalse(hasConsentedInitially);
		Boolean initialConsent = factory.withTransaction(tx -> sut.updateConsentToRetailer(tx, userId, retailerId, true))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertNull(initialConsent, "The initial consent does not exist");
		System.out.println("XXXXXXXXXXXXXXXXXXX");
		var cccc = factory.withTransaction(tx -> {
//			var cb = sessionFactory.getCriteriaBuilder();
			var cb = tx.getCriteriaBuilder();
			var query = cb.createQuery(ConsentEntity.class);
			Root<ConsentEntity> root = query.from(ConsentEntity.class);
			query.where(
					cb.equal(root.get(ConsentEntity_.user).get(UserProfileEntity_.id), USER_ID),
					cb.equal(root.get(ConsentEntity_.retailer).get(RetailerEntity_.id), RETAILER_ID)
			);
			return tx.createQuery(query).getSingleResult();
		}).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var cccc1 = sessionFactory.withTransaction(session -> {
//			var cb = sessionFactory.getCriteriaBuilder();
			var cb = session.getFactory().getCriteriaBuilder();
			var query = cb.createQuery(ConsentEntity.class);
			Root<ConsentEntity> root = query.from(ConsentEntity.class);
			query.where(
					cb.equal(root.get(ConsentEntity_.user).get(UserProfileEntity_.id), USER_ID),
					cb.equal(root.get(ConsentEntity_.retailer).get(RetailerEntity_.id), RETAILER_ID)
			);
			return session.createQuery(query).getSingleResult();
		}).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var ccc = sessionFactory.withTransaction(session -> {
			var id = new ConsentPK(USER_ID, RETAILER_ID);
			return session.find(ConsentEntity.class, id);
		}).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var cc = sessionFactory.withTransaction(session -> {
			var q = session.createQuery("SELECT c FROM ConsentEntity c WHERE c.user.id = :userId AND c.retailer.id = :retailerId", ConsentEntity.class);
			q.setParameter("userId", USER_ID);
			q.setParameter("retailerId", RETAILER_ID);
			return q.getSingleResultOrNull();
		}).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		Boolean hasConsentedAfterGrant = factory.withTransaction(tx -> sut.hasConsentedToRetailer(tx, userId, retailerId))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		System.out.println("YYYYYYYYYYYYYYYYYYY");
		assertTrue(hasConsentedAfterGrant);
		Boolean previousConsent = factory.withTransaction(tx -> sut.updateConsentToRetailer(tx, userId, retailerId, false))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertTrue(previousConsent);
		Boolean hasConsentedFinally = factory.withTransaction(tx -> sut.hasConsentedToRetailer(tx, userId, retailerId))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertFalse(hasConsentedFinally);
		System.out.println("ZZZZZZZZZZZZZZZZZZZ");
		var ccccc = sessionFactory.withTransaction(session -> {
//			var cb = sessionFactory.getCriteriaBuilder();
			var cb = session.getFactory().getCriteriaBuilder();
			var query = cb.createQuery(ConsentEntity.class);
			Root<ConsentEntity> root = query.from(ConsentEntity.class);
			query.where(
					cb.equal(root.get(ConsentEntity_.user).get(UserProfileEntity_.id), USER_ID),
					cb.equal(root.get(ConsentEntity_.retailer).get(RetailerEntity_.id), RETAILER_ID)
			);
			return session.createQuery(query).getSingleResult();
		}).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var ccccc1 = factory.withTransaction(tx -> {
//			var cb = sessionFactory.getCriteriaBuilder();
			var cb = tx.getCriteriaBuilder();
			var query = cb.createQuery(ConsentEntity.class);
			Root<ConsentEntity> root = query.from(ConsentEntity.class);
			query.where(
					cb.equal(root.get(ConsentEntity_.user).get(UserProfileEntity_.id), USER_ID),
					cb.equal(root.get(ConsentEntity_.retailer).get(RetailerEntity_.id), RETAILER_ID)
			);
			return tx.createQuery(query).getSingleResult();
		}).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}
}
