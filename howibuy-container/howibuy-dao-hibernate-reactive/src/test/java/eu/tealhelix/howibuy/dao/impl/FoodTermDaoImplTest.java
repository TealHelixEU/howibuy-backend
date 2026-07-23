package eu.tealhelix.howibuy.dao.impl;

import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import eu.tealhelix.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.tealhelix.common.test.jpa.HibernateReactiveExtension;
import eu.tealhelix.common.test.liquibase.LiquibaseExtension;
import eu.tealhelix.howibuy.dao.jpa.FoodTermEntity;
import eu.tealhelix.howibuy.services.model.FoodTerm;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
public class FoodTermDaoImplTest {
	private static final Duration WAIT = Duration.ofSeconds(300);

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
	 * Seed a few terms in two languages once; both tests only read them. The Greek "Βλήτα" carries no category hint, to
	 * exercise the nullable category-hint columns.
	 */
	@BeforeAll
	static void seedGlossary(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var anthotyros = foodTerm("el", "Ανθότυρος", "anthotyros", "Greek whey cheese, similar to ricotta or mizithra", "Milk and dairy products", null, null);
		var vlita = foodTerm("el", "Βλήτα", "amaranth greens", "amaranth greens, a leafy green vegetable", null, null, null);
		var varske = foodTerm("lt", "Varškė", "curd cheese", "fresh curd cheese", "Milk and dairy products", null, null);
		factory.withTransaction(tx -> tx.persistAll(anthotyros, vlita, varske)).await().atMost(WAIT);
	}

	@Test
	void retrievesOnlyTermsOfGivenLanguage(Mutiny.SessionFactory sessionFactory) {
		var sut = new FoodTermDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var greekTerms = factory.withoutTransaction(em -> sut.retrieveByLanguage(em, "el"))
				.subscribe().withSubscriber(UniAssertSubscriber.create())
				.awaitItem(WAIT).getItem();

		assertEquals(
				Map.of("Ανθότυρος", "anthotyros", "Βλήτα", "amaranth greens"),
				greekTerms.stream().collect(toMap(FoodTerm::getTerm, FoodTerm::getCanonicalEn)),
				"only the Greek terms, projected to term + canonical English name; the Lithuanian term is excluded");
	}

	@Test
	void carriesDescriptionAndOptionalCategoryHint(Mutiny.SessionFactory sessionFactory) {
		var sut = new FoodTermDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var byTerm = factory.withoutTransaction(em -> sut.retrieveByLanguage(em, "el"))
				.subscribe().withSubscriber(UniAssertSubscriber.create())
				.awaitItem(WAIT).getItem()
				.stream().collect(toMap(FoodTerm::getTerm, Function.identity()));

		var anthotyros = byTerm.get("Ανθότυρος");
		assertEquals("Greek whey cheese, similar to ricotta or mizithra", anthotyros.getDescription(), "description carried verbatim");
		assertEquals(Optional.of("Milk and dairy products"), anthotyros.getCategoryHint(), "present category hint");
		assertEquals(Optional.empty(), byTerm.get("Βλήτα").getCategoryHint(), "absent category hint maps to empty Optional");
	}

	private static FoodTermEntity foodTerm(String lang, String term, String canonicalEn, String description,
			String categoryHintL1, String categoryHintL2, String categoryHintL3) {
		var entity = new FoodTermEntity();
		entity.setId(UUID.randomUUID());
		entity.setLang(lang);
		entity.setTerm(term);
		entity.setCanonicalEn(canonicalEn);
		entity.setDescription(description);
		entity.setCategoryHintL1(categoryHintL1);
		entity.setCategoryHintL2(categoryHintL2);
		entity.setCategoryHintL3(categoryHintL3);
		return entity;
	}
}
