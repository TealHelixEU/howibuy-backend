package eu.tealhelix.sfc.dao.impl;

import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import eu.tealhelix.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.tealhelix.common.test.jpa.HibernateReactiveExtension;
import eu.tealhelix.common.test.liquibase.LiquibaseExtension;
import eu.tealhelix.sfc.dao.jpa.AnswerEntity;
import eu.tealhelix.sfc.dao.jpa.AnswerEntityId;
import eu.tealhelix.sfc.dao.jpa.AttemptEntity;
import eu.tealhelix.sfc.dao.jpa.CategoryEntity;
import eu.tealhelix.sfc.dao.jpa.QuestionEntity;
import eu.tealhelix.sfc.v1.types.AttemptStatus;
import eu.tealhelix.sfc.v1.types.ScaleOption;
import eu.tealhelix.sfc.v1.types.SustainabilityDimension;
import eu.tealhelix.sfc.v1.types.impl.QuestionIdImpl;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The answer DAO against a real database: {@code upsert} inserts a new answer, overwrites an existing one in place (the
 * {@code (attempt, question)} primary key means re-answering never adds a row), and keeps answers to different
 * questions on the same attempt apart; {@code retrieveByAttempt} reads them all back keyed by question. The schema is
 * created from the SFC changelog via the test stub that supplies the cross-module {@code TH_USER_PROFILE}; the owning
 * user is seeded over JDBC since the profile is not a JPA entity in this module, and its category, questions and
 * in-progress attempt over the reactive session.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Testcontainers
public class AnswerDaoImplTest {
	private static final Duration WAIT = Duration.ofSeconds(300);

	private static final UUID USER_ID = UUID.fromString("e0000000-0000-0000-0000-000000000001");
	private static final UUID CATEGORY_ID = UUID.fromString("e1000000-0000-0000-0000-000000000001");
	private static final UUID QUESTION_1_ID = UUID.fromString("e2000000-0000-0000-0000-000000000001");
	private static final UUID QUESTION_2_ID = UUID.fromString("e2000000-0000-0000-0000-000000000002");
	private static final UUID ATTEMPT_ID = UUID.fromString("e3000000-0000-0000-0000-000000000001");

	@Container
	private static final PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGRES_IMAGE);

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final LiquibaseExtension liquibaseExtension =
			new LiquibaseExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword(), "test.db.changelog.xml", "test");

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final HibernateReactiveExtension hibernateReactiveExtension =
			new HibernateReactiveExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword());

	private final AnswerDaoImpl sut = new AnswerDaoImpl();

	@Test
	@Order(1)
	void seed(Mutiny.SessionFactory sessionFactory) throws SQLException {
		insertUserProfile(USER_ID);

		var category = category(CATEGORY_ID, SustainabilityDimension.ECOLOGICAL);
		var attempt = new AttemptEntity();
		attempt.setId(ATTEMPT_ID);
		attempt.setUserId(USER_ID);
		attempt.setStatus(AttemptStatus.IN_PROGRESS);

		factory(sessionFactory).withTransaction(tx ->
				tx.persist(category)
						.chain(() -> tx.flush())
						.chain(() -> tx.persistAll(
								question(QUESTION_1_ID, category, (short) 1),
								question(QUESTION_2_ID, category, (short) 2),
								attempt))
		).await().atMost(WAIT);
	}

	@Test
	@Order(2)
	void insertsANewAnswer(Mutiny.SessionFactory sessionFactory) {
		factory(sessionFactory).withTransaction(tx ->
				sut.upsert(tx, ATTEMPT_ID, new QuestionIdImpl(QUESTION_1_ID.toString()), ScaleOption.MODERATELY_IMPORTANT)).await().atMost(WAIT);

		assertEquals(ScaleOption.MODERATELY_IMPORTANT.getValue(), answerValue(sessionFactory, QUESTION_1_ID),
				"the chosen option's ordinal was persisted");
		assertEquals(1L, answerCount(sessionFactory), "exactly one answer saved");
	}

	@Test
	@Order(3)
	void overwritesAnExistingAnswerInPlace(Mutiny.SessionFactory sessionFactory) {
		factory(sessionFactory).withTransaction(tx ->
				sut.upsert(tx, ATTEMPT_ID, new QuestionIdImpl(QUESTION_1_ID.toString()), ScaleOption.EXTREMELY_IMPORTANT)).await().atMost(WAIT);

		assertEquals(ScaleOption.EXTREMELY_IMPORTANT.getValue(), answerValue(sessionFactory, QUESTION_1_ID), "the latest choice won");
		assertEquals(1L, answerCount(sessionFactory), "re-answering overwrote rather than adding a row");
	}

	@Test
	@Order(4)
	void keepsAnswersToDifferentQuestionsApart(Mutiny.SessionFactory sessionFactory) {
		factory(sessionFactory).withTransaction(tx ->
				sut.upsert(tx, ATTEMPT_ID, new QuestionIdImpl(QUESTION_2_ID.toString()), ScaleOption.NOT_IMPORTANT)).await().atMost(WAIT);

		assertEquals(ScaleOption.NOT_IMPORTANT.getValue(), answerValue(sessionFactory, QUESTION_2_ID), "the second question's answer");
		assertEquals(ScaleOption.EXTREMELY_IMPORTANT.getValue(), answerValue(sessionFactory, QUESTION_1_ID), "the first question's answer is untouched");
		assertEquals(2L, answerCount(sessionFactory), "both answers are kept");
	}

	@Test
	@Order(5)
	void retrievesAllAnswersOnTheAttemptKeyedByQuestion(Mutiny.SessionFactory sessionFactory) {
		var answers = factory(sessionFactory)
				.withoutTransaction(em -> sut.retrieveByAttempt(em, ATTEMPT_ID)).await().atMost(WAIT);

		assertEquals(Map.of(
				new QuestionIdImpl(QUESTION_1_ID.toString()), ScaleOption.EXTREMELY_IMPORTANT,
				new QuestionIdImpl(QUESTION_2_ID.toString()), ScaleOption.NOT_IMPORTANT), answers,
				"both answers come back keyed by their question");
	}

	@Test
	@Order(6)
	void retrievesNoAnswersForAnAttemptThatHasNone(Mutiny.SessionFactory sessionFactory) {
		var answers = factory(sessionFactory)
				.withoutTransaction(em -> sut.retrieveByAttempt(em, UUID.randomUUID())).await().atMost(WAIT);

		assertTrue(answers.isEmpty(), "an attempt with no answers yields an empty map");
	}

	private short answerValue(Mutiny.SessionFactory sessionFactory, UUID questionId) {
		return factory(sessionFactory)
				.withoutTransaction(em -> em.find(AnswerEntity.class, new AnswerEntityId(ATTEMPT_ID, questionId)))
				.await().atMost(WAIT).getValue();
	}

	private static long answerCount(Mutiny.SessionFactory sessionFactory) {
		return sessionFactory.withSession(s ->
				s.createQuery("select count(a) from AnswerEntity a", Long.class).getSingleResult()).await().atMost(WAIT);
	}

	private static ReactivePersistenceContextFactoryImpl factory(Mutiny.SessionFactory sessionFactory) {
		return new ReactivePersistenceContextFactoryImpl(sessionFactory);
	}

	private static CategoryEntity category(UUID id, SustainabilityDimension dimension) {
		var category = new CategoryEntity();
		category.setId(id);
		category.setDimension(dimension);
		return category;
	}

	private static QuestionEntity question(UUID id, CategoryEntity category, short position) {
		var question = new QuestionEntity();
		question.setId(id);
		question.setCategory(category);
		question.setPosition(position);
		return question;
	}

	private static void insertUserProfile(UUID id) throws SQLException {
		try (var c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
				var s = c.prepareStatement("INSERT INTO TH_USER_PROFILE (id) VALUES (?)")) {
			s.setObject(1, id);
			s.executeUpdate();
		}
	}
}
