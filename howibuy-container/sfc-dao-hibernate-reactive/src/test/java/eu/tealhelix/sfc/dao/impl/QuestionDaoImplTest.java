package eu.tealhelix.sfc.dao.impl;

import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import eu.tealhelix.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.tealhelix.common.test.jpa.HibernateReactiveExtension;
import eu.tealhelix.common.test.liquibase.LiquibaseExtension;
import eu.tealhelix.sfc.dao.jpa.CategoryEntity;
import eu.tealhelix.sfc.dao.jpa.QuestionEntity;
import eu.tealhelix.sfc.dao.jpa.QuestionTextEntity;
import eu.tealhelix.sfc.v1.model.Question;
import eu.tealhelix.sfc.v1.types.SustainabilityDimension;
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
 * The question read against a real database: the {@code (question, category, text)} join is resolved for the requested
 * language, a single-category read returns only that category's questions in position order, and the all-questions read
 * orders by dimension then position. Questions are inserted out of order to prove the ordering comes from the query.
 * The schema is created from the SFC changelog (without the {@code appdata} seed) and a small controlled fixture is
 * inserted here.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Testcontainers
public class QuestionDaoImplTest {
	private static final Duration WAIT = Duration.ofSeconds(300);

	private static final UUID ECOLOGICAL_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID HEALTH_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final UUID ECOLOGICAL_Q1_ID = UUID.fromString("a1111111-1111-1111-1111-111111111111");
	private static final UUID ECOLOGICAL_Q2_ID = UUID.fromString("a2222222-2222-2222-2222-222222222222");
	private static final UUID HEALTH_Q1_ID = UUID.fromString("b1111111-1111-1111-1111-111111111111");

	@Container
	private static final PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGRES_IMAGE);

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final LiquibaseExtension liquibaseExtension =
			new LiquibaseExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword(), "sfc.db.changelog.xml", "test");

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final HibernateReactiveExtension hibernateReactiveExtension =
			new HibernateReactiveExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword());

	private final QuestionDaoImpl sut = new QuestionDaoImpl();

	@Test
	@Order(1)
	void seed(Mutiny.SessionFactory sessionFactory) {
		var ecological = category(ECOLOGICAL_ID, SustainabilityDimension.ECOLOGICAL);
		var health = category(HEALTH_ID, SustainabilityDimension.HEALTH);
		var ecologicalQ1 = question(ECOLOGICAL_Q1_ID, ecological, (short) 1);
		var ecologicalQ2 = question(ECOLOGICAL_Q2_ID, ecological, (short) 2);
		var healthQ1 = question(HEALTH_Q1_ID, health, (short) 1);
		factory(sessionFactory).withTransaction(tx ->
				tx.persistAll(ecological, health)
						.chain(() -> tx.flush())
						// deliberately out of position/dimension order
						.chain(() -> tx.persistAll(ecologicalQ2, healthQ1, ecologicalQ1))
						.chain(() -> tx.flush())
						.chain(() -> tx.persistAll(
								questionText(ecologicalQ1, "en", "Ecological Q1, in English"),
								questionText(ecologicalQ1, "el", "Ecological Q1, στα ελληνικά"),
								questionText(ecologicalQ2, "en", "Ecological Q2, in English"),
								questionText(ecologicalQ2, "el", "Ecological Q2, στα ελληνικά"),
								questionText(healthQ1, "en", "Health Q1, in English"),
								questionText(healthQ1, "el", "Health Q1, στα ελληνικά")))
		).await().atMost(WAIT);
	}

	@Test
	@Order(2)
	void retrievesOnlyTheRequestedCategorysQuestionsInPositionOrder(Mutiny.SessionFactory sessionFactory) {
		var questions = factory(sessionFactory)
				.withoutTransaction(em -> sut.retrieveByCategoryAndLanguage(em, ECOLOGICAL_ID, "en")).await().atMost(WAIT);

		assertEquals(List.of(ECOLOGICAL_Q1_ID, ECOLOGICAL_Q2_ID), questions.stream().map(Question::getId).toList(),
				"only the ecological category's questions, in position order");
		assertEquals(List.of((short) 1, (short) 2), questions.stream().map(Question::getPosition).toList(), "position order");
		assertEquals(List.of("Ecological Q1, in English", "Ecological Q2, in English"),
				questions.stream().map(Question::getText).toList(), "prompts resolved for English");
		questions.forEach(q -> assertEquals(ECOLOGICAL_ID, q.getCategoryId(), "carries its category id"));
	}

	@Test
	@Order(3)
	void retrievesAllQuestionsOrderedByDimensionThenPosition(Mutiny.SessionFactory sessionFactory) {
		var questions = factory(sessionFactory)
				.withoutTransaction(em -> sut.retrieveByLanguage(em, "en")).await().atMost(WAIT);

		assertEquals(List.of(ECOLOGICAL_Q1_ID, ECOLOGICAL_Q2_ID, HEALTH_Q1_ID), questions.stream().map(Question::getId).toList(),
				"ecological (by position) before health");
		assertEquals(List.of(ECOLOGICAL_ID, ECOLOGICAL_ID, HEALTH_ID), questions.stream().map(Question::getCategoryId).toList(),
				"grouped by category");
	}

	@Test
	@Order(4)
	void resolvesPromptToTheRequestedLanguage(Mutiny.SessionFactory sessionFactory) {
		var questions = factory(sessionFactory)
				.withoutTransaction(em -> sut.retrieveByLanguage(em, "el")).await().atMost(WAIT);

		assertEquals(
				List.of("Ecological Q1, στα ελληνικά", "Ecological Q2, στα ελληνικά", "Health Q1, στα ελληνικά"),
				questions.stream().map(Question::getText).toList(),
				"prompts resolved for Greek");
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

	private static QuestionTextEntity questionText(QuestionEntity question, String lang, String text) {
		var questionText = new QuestionTextEntity();
		questionText.setQuestion(question);
		questionText.setLang(lang);
		questionText.setText(text);
		return questionText;
	}
}
