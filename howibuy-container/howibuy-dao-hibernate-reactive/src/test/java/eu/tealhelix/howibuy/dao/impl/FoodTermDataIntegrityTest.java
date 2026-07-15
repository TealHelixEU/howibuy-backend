package eu.tealhelix.howibuy.dao.impl;

import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

import eu.tealhelix.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.tealhelix.common.test.jpa.HibernateReactiveExtension;
import eu.tealhelix.common.test.liquibase.LiquibaseExtension;
import eu.tealhelix.howibuy.services.model.FoodTerm;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Verifies that the real food-term glossary seed data (the {@code appdata} Liquibase context) imports correctly: the
 * CSV parses — including quoted descriptions that contain commas — and known Greek terms resolve to their English
 * canonical name, description and category hint. Asserts specific rows rather than a total count, since the glossary is
 * meant to grow.
 */
@Testcontainers
public class FoodTermDataIntegrityTest {
	private static final Duration WAIT = Duration.ofSeconds(300);

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
	void loadsKnownGreekTermsWithTheirEnrichment(Mutiny.SessionFactory sessionFactory) {
		var sut = new FoodTermDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var byTerm = factory.withoutTransaction(em -> sut.retrieveByLanguage(em, "el"))
				.subscribe().withSubscriber(UniAssertSubscriber.create())
				.awaitItem(WAIT).getItem()
				.stream().collect(toMap(FoodTerm::getTerm, Function.identity()));

		var vlita = byTerm.get("Βλήτα");
		assertEquals("amaranth greens", vlita.getCanonicalEn(), "Βλήτα canonical English name");
		assertEquals("amaranth greens (Amaranthus), a summer horta eaten boiled", vlita.getDescription(), "quoted description with comma parsed intact");
		assertEquals(Optional.of("Vegetables and vegetable products (including fungi) → Leaf vegetables"), vlita.getCategoryHint(), "Βλήτα category-hint path");

		var anthotyros = byTerm.get("Ανθότυρος");
		assertEquals("anthotyros", anthotyros.getCanonicalEn(), "Ανθότυρος canonical English name");
		assertEquals("Greek whey cheese, similar to ricotta or mizithra", anthotyros.getDescription(), "quoted description with comma parsed intact");
		assertEquals(Optional.of("Milk and dairy products → Cheese"), anthotyros.getCategoryHint(), "Ανθότυρος category-hint path");
	}
}
