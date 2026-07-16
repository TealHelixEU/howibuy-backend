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
		assertEquals(Optional.of("Milk and dairy products → Cheese"), anthotyros.getCategoryHint(), "Ανθότυρος (whey cheese) hint stops at L2 — no SAFAD archetype");
	}

	@Test
	void resolvesCheeseTermsToTheirL3CategoryHint(Mutiny.SessionFactory sessionFactory) {
		var sut = new FoodTermDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var byTerm = factory.withoutTransaction(em -> sut.retrieveByLanguage(em, "el"))
				.subscribe().withSubscriber(UniAssertSubscriber.create())
				.awaitItem(WAIT).getItem()
				.stream().collect(toMap(FoodTerm::getTerm, Function.identity()));

		var graviera = byTerm.get("Γραβιέρα");
		assertEquals(Optional.of("Cheese, Manchego"), graviera.getCategoryHintL3(), "Γραβιέρα (Cretan ewe cheese) L3 hint");
		assertEquals(Optional.of("Milk and dairy products → Cheese → Cheese, Manchego"), graviera.getCategoryHint(), "Γραβιέρα full hint path");

		assertEquals(Optional.of("Cheese, Manchego"), byTerm.get("Κεφαλοτύρι").getCategoryHintL3(), "Κεφαλοτύρι L3 hint");
		assertEquals(Optional.of("Cheese, Edam"), byTerm.get("Edam").getCategoryHintL3(), "Edam L3 hint");
		assertEquals(Optional.of("Fresh uncured cheese"), byTerm.get("Τυρί Κρέμα").getCategoryHintL3(), "cream cheese L3 hint");
	}

	@Test
	void resolvesBakeryTermsToTheirL3CategoryHint(Mutiny.SessionFactory sessionFactory) {
		var sut = new FoodTermDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var byTerm = factory.withoutTransaction(em -> sut.retrieveByLanguage(em, "el"))
				.subscribe().withSubscriber(UniAssertSubscriber.create())
				.awaitItem(WAIT).getItem()
				.stream().collect(toMap(FoodTerm::getTerm, Function.identity()));

		// breadsticks: the singular is deepened and the plural forms are added, both to "Other bread"
		assertEquals(Optional.of("Other bread"), byTerm.get("Κριτσίνι").getCategoryHintL3(), "Κριτσίνι (breadstick) L3 hint");
		assertEquals(Optional.of("Other bread"), byTerm.get("Κριτσίνια").getCategoryHintL3(), "Κριτσίνια (breadsticks) L3 hint");

		// rusks: singular and plural diminutive both classify as rusk (unified with Παξιμάδι)
		assertEquals(Optional.of("Unleavened bread, crisp bread and rusk"), byTerm.get("Παξιμαδάκι").getCategoryHintL3(), "Παξιμαδάκι (rusk) L3 hint");
		assertEquals(Optional.of("Unleavened bread, crisp bread and rusk"), byTerm.get("Παξιμαδάκια").getCategoryHintL3(), "Παξιμαδάκια (rusks) L3 hint");

		// cheese pies: existing terms deepened, diminutive-plural forms added, both to "Pastries and cakes"
		assertEquals(Optional.of("Pastries and cakes"), byTerm.get("Μυζηθρόπιτα").getCategoryHintL3(), "Μυζηθρόπιτα L3 hint");
		assertEquals(Optional.of("Pastries and cakes"), byTerm.get("Μυζηθροπιτάκια").getCategoryHintL3(), "Μυζηθροπιτάκια L3 hint");

		// biscuits: existing term deepened, plural form added, both to "Biscuits (cookies)"
		assertEquals(Optional.of("Biscuits (cookies)"), byTerm.get("Μπισκότο").getCategoryHintL3(), "Μπισκότο L3 hint");
		assertEquals(Optional.of("Biscuits (cookies)"), byTerm.get("Μπισκότα").getCategoryHintL3(), "Μπισκότα L3 hint");
	}
}
