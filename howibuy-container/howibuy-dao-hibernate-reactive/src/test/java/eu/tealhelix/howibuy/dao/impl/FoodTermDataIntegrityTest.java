package eu.tealhelix.howibuy.dao.impl;

import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.util.Map;
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
 * per-language CSVs parse — including quoted descriptions that contain commas — and known terms resolve to their
 * English canonical name, description and category hint under the language they were seeded for. Asserts specific rows
 * rather than a total count, since the glossary is meant to grow.
 */
@Testcontainers
public class FoodTermDataIntegrityTest {
	private static final Duration WAIT = Duration.ofSeconds(300);

	@Container
	private static final PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGRES_IMAGE);

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final LiquibaseExtension liquibaseExtension =
			new LiquibaseExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword(), "howibuy.db.changelog.xml", "appdata");

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final HibernateReactiveExtension hibernateReactiveExtension =
			new HibernateReactiveExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword());

	/**
	 * The glossary of the given language as seeded in the database, keyed by term.
	 */
	private Map<String, FoodTerm> glossaryOf(Mutiny.SessionFactory sessionFactory, String lang) {
		var sut = new FoodTermDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		return factory.withoutTransaction(em -> sut.retrieveByLanguage(em, lang))
				.subscribe().withSubscriber(UniAssertSubscriber.create())
				.awaitItem(WAIT).getItem()
				.stream().collect(toMap(FoodTerm::getTerm, Function.identity()));
	}

	@Test
	void loadsKnownGreekTermsWithTheirEnrichment(Mutiny.SessionFactory sessionFactory) {
		var byTerm = glossaryOf(sessionFactory, "el");

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
		var byTerm = glossaryOf(sessionFactory, "el");

		var graviera = byTerm.get("Γραβιέρα");
		assertEquals(Optional.of("Cheese, Manchego"), graviera.getCategoryHintL3(), "Γραβιέρα (Cretan ewe cheese) L3 hint");
		assertEquals(Optional.of("Milk and dairy products → Cheese → Cheese, Manchego"), graviera.getCategoryHint(), "Γραβιέρα full hint path");

		assertEquals(Optional.of("Cheese, Manchego"), byTerm.get("Κεφαλοτύρι").getCategoryHintL3(), "Κεφαλοτύρι L3 hint");
		assertEquals(Optional.of("Cheese, Edam"), byTerm.get("Edam").getCategoryHintL3(), "Edam L3 hint");
		assertEquals(Optional.of("Fresh uncured cheese"), byTerm.get("Τυρί Κρέμα").getCategoryHintL3(), "cream cheese L3 hint");
	}

	@Test
	void resolvesBakeryTermsToTheirL3CategoryHint(Mutiny.SessionFactory sessionFactory) {
		var byTerm = glossaryOf(sessionFactory, "el");

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

	@Test
	void resolvesMeatCutTermsToTheirEnrichment(Mutiny.SessionFactory sessionFactory) {
		var byTerm = glossaryOf(sessionFactory, "el");

		// A species-qualified cut (a two-token term, disambiguating the species) carries that species' L3 hint.
		var beefEye = byTerm.get("Βόειο Ελιά");
		assertEquals("eye of round", beefEye.getCanonicalEn(), "Βόειο Ελιά canonical English name");
		assertEquals(Optional.of("Beef meat (Bos spp.)"), beefEye.getCategoryHintL3(), "Βόειο Ελιά L3 hint (beef)");
		assertEquals("chop", byTerm.get("Χοιρινό Μπριζόλα").getCanonicalEn(), "Χοιρινό Μπριζόλα (pork chop) canonical English name");
		assertEquals(Optional.of("Pork / piglet meat (Sus scrofa)"), byTerm.get("Χοιρινό Μπριζόλα").getCategoryHintL3(), "Χοιρινό Μπριζόλα L3 hint (pork)");

		// A single-species butcher word carries that species' L3 hint on its own.
		assertEquals(Optional.of("Pork / piglet meat (Sus scrofa)"), byTerm.get("Ψαρονέφρι").getCategoryHintL3(), "Ψαρονέφρι (pork tenderloin) L3 hint");
		assertEquals("wings", byTerm.get("Φτερούγες").getCanonicalEn(), "Φτερούγες canonical English name");
		assertEquals(Optional.of("Chicken meat (Gallus domesticus)"), byTerm.get("Φτερούγες").getCategoryHintL3(), "Φτερούγες (chicken wings) L3 hint");

		// A cut shared across livestock species: the word alone does not imply the species, so the hint stops at L2.
		var shoulder = byTerm.get("Σπάλα");
		assertEquals("shoulder", shoulder.getCanonicalEn(), "Σπάλα canonical English name");
		assertEquals(Optional.of("Livestock meat"), shoulder.getCategoryHintL2(), "Σπάλα L2 hint");
		assertEquals(Optional.empty(), shoulder.getCategoryHintL3(), "Σπάλα carries no L3 hint (species not implied by the word)");
	}

	@Test
	void loadsKnownLithuanianTermsWithTheirEnrichment(Mutiny.SessionFactory sessionFactory) {
		var byTerm = glossaryOf(sessionFactory, "lt");

		var kastinys = byTerm.get("Kastinys");
		assertEquals("kastinys", kastinys.getCanonicalEn(), "Kastinys canonical English name");
		assertEquals("kastinys, a Samogitian whipped soured-cream spread", kastinys.getDescription(), "quoted description with comma parsed intact");
		assertEquals(Optional.of("Milk and dairy products → Cream and cream products → Cream"), kastinys.getCategoryHint(), "Kastinys category-hint path");

		assertEquals(Optional.of("Fresh uncured cheese"), byTerm.get("Varškė").getCategoryHintL3(), "Varškė (fresh curd) L3 hint");
		assertEquals(Optional.of("Kefir"), byTerm.get("Kefyras").getCategoryHintL3(), "Kefyras L3 hint");
		assertEquals(Optional.of("Beef meat (Bos spp.)"), byTerm.get("Jautiena").getCategoryHintL3(), "Jautiena (beef) L3 hint");
		assertEquals(Optional.of("Pork / piglet meat (Sus scrofa)"), byTerm.get("Kiauliena").getCategoryHintL3(), "Kiauliena (pork) L3 hint");
		assertEquals(Optional.of("Mixed wheat and rye bread and rolls"), byTerm.get("Ruginė duona").getCategoryHintL3(), "Ruginė duona (rye bread) L3 hint");
	}

	@Test
	void loadsTheInflectedFormsLithuanianCompoundsAreWrittenIn(Mutiny.SessionFactory sessionFactory) {
		var byTerm = glossaryOf(sessionFactory, "lt");

		// Lithuanian writes the qualifier of a compound in the genitive, so the genitive is a term of its own — the
		// nominative alone would never match "Pieno gėrimas".
		var pieno = byTerm.get("Pieno");
		assertEquals("milk (of milk)", pieno.getCanonicalEn(), "Pieno canonical English name");
		assertEquals(Optional.of("Milk and dairy products"), pieno.getCategoryHint(), "Pieno hint stops at L1 — the compound decides the product");
		assertEquals(Optional.of("Milk and dairy products → Liquid milk"), byTerm.get("Pienas").getCategoryHint(), "Pienas (nominative) reaches L2");

		var tresniu = byTerm.get("Trešnių");
		assertEquals("sweet cherry (of cherries)", tresniu.getCanonicalEn(), "Trešnių canonical English name");
		assertEquals(Optional.of("Fruit and fruit products → Stone fruits → Sweet cherry (Prunus avium)"), tresniu.getCategoryHint(), "Trešnių (genitive plural) category-hint path");
	}

	@Test
	void stopsTheHintWhereTheLithuanianWordStopsBeingHonest(Mutiny.SessionFactory sessionFactory) {
		var byTerm = glossaryOf(sessionFactory, "lt");

		// A word naming both a fresh cut and the cured product made from it locates no deeper than the L1 branch.
		var kumpis = byTerm.get("Kumpis");
		assertEquals("ham or leg", kumpis.getCanonicalEn(), "Kumpis canonical English name");
		assertEquals(Optional.of("Meat and meat products (including edible offal)"), kumpis.getCategoryHintL1(), "Kumpis L1 hint");
		assertEquals(Optional.empty(), kumpis.getCategoryHintL2(), "Kumpis carries no L2 hint (fresh leg and cured ham are different branches)");

		assertEquals(Optional.of("Grain milling products"), byTerm.get("Miltai").getCategoryHintL2(), "Miltai (flour) L2 hint");
		assertEquals(Optional.empty(), byTerm.get("Miltai").getCategoryHintL3(), "Miltai carries no L3 hint (the grain is not implied by the word)");

		// A word that spans top-level branches carries its meaning with no hint at all.
		assertEquals("small cheese or curd bar", byTerm.get("Sūrelis").getCanonicalEn(), "Sūrelis canonical English name");
		assertEquals(Optional.empty(), byTerm.get("Sūrelis").getCategoryHint(), "Sūrelis carries no hint (processed cheese and glazed curd bar are different branches)");
		assertEquals(Optional.empty(), byTerm.get("Gėrimas").getCategoryHint(), "Gėrimas (drink) carries no hint");
	}

	@Test
	void keepsTheGlossariesOfTheTwoLanguagesApart(Mutiny.SessionFactory sessionFactory) {
		var greek = glossaryOf(sessionFactory, "el");
		var lithuanian = glossaryOf(sessionFactory, "lt");

		assertNull(lithuanian.get("Ανθότυρος"), "a Greek term is not part of the Lithuanian glossary");
		assertNull(greek.get("Kastinys"), "a Lithuanian term is not part of the Greek glossary");
	}
}
