package eu.tealhelix.howibuy.dao.impl;

import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import eu.tealhelix.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.tealhelix.common.test.jpa.HibernateReactiveExtension;
import eu.tealhelix.common.test.liquibase.LiquibaseExtension;
import eu.tealhelix.howibuy.dao.jpa.ArchetypeCategoryEntity;
import eu.tealhelix.howibuy.dao.jpa.ArchetypeProductEntity;
import eu.tealhelix.howibuy.dao.jpa.values.AnimalWelfareImpact;
import eu.tealhelix.howibuy.dao.jpa.values.EnvironmentalImpact;
import eu.tealhelix.howibuy.dao.jpa.values.SocialImpact;
import eu.tealhelix.howibuy.services.model.ArchetypeProduct;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
public class ArchetypeProductDaoImplTest {
	private static final Duration WAIT = Duration.ofSeconds(300);

	private static final UUID L3_JUICES = UUID.fromString("00000000-0000-0000-0000-000000000101");
	private static final UUID L3_MILKS = UUID.fromString("00000000-0000-0000-0000-000000000102");
	private static final UUID ORANGE_JUICE = UUID.fromString("00000000-0000-0000-0000-000000000201");
	private static final UUID GENERIC_JUICE = UUID.fromString("00000000-0000-0000-0000-000000000202");
	private static final UUID WHOLE_MILK = UUID.fromString("00000000-0000-0000-0000-000000000203");
	private static final UUID GENERIC_MILK = UUID.fromString("00000000-0000-0000-0000-000000000204");

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

	/**
	 * Seed two L3 categories, each holding archetype products; "Generic" appears under both to exercise the
	 * category-scoped (not name-based) product lookup.
	 * <pre>
	 * Juices (L3)              Milks (L3)
	 *   Orange juice             Whole milk
	 *   Generic                  Generic
	 * </pre>
	 */
	@BeforeAll
	static void seedProducts(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var juices = category(L3_JUICES, "Juices");
		var milks = category(L3_MILKS, "Milks");
		var orangeJuice = product(ORANGE_JUICE, juices, "Orange juice", "agb-oj");
		var genericJuice = product(GENERIC_JUICE, juices, "Generic", "agb-gj");
		var wholeMilk = product(WHOLE_MILK, milks, "Whole milk", "agb-wm");
		var genericMilk = product(GENERIC_MILK, milks, "Generic", "agb-gm");
		factory.withTransaction(tx -> tx.persistAll(juices, milks, orangeJuice, genericJuice, wholeMilk, genericMilk))
				.await().atMost(WAIT);
	}

	@Test
	void retrievesProductsOfGivenCategoryOnly(Mutiny.SessionFactory sessionFactory) {
		var sut = new ArchetypeProductDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var products = factory.withoutTransaction(em -> sut.retrieveProductsInCategory(em, L3_JUICES))
				.subscribe().withSubscriber(UniAssertSubscriber.create())
				.awaitItem(WAIT).getItem();

		assertEquals(
				Map.of(ORANGE_JUICE, "Orange juice", GENERIC_JUICE, "Generic"),
				byIdAndName(products),
				"only Juices' products: Milks' products, including the identically-named 'Generic', are excluded");
	}

	@Test
	void retrievesProductsOrderedByName(Mutiny.SessionFactory sessionFactory) {
		var sut = new ArchetypeProductDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var products = factory.withoutTransaction(em -> sut.retrieveProductsInCategory(em, L3_JUICES))
				.subscribe().withSubscriber(UniAssertSubscriber.create())
				.awaitItem(WAIT).getItem();

		assertEquals(
				List.of("Generic", "Orange juice"),
				names(products),
				"candidates are ordered by name so the classifier prompt is deterministic (seeded reverse-alphabetically)");
	}

	private static Map<UUID, String> byIdAndName(List<ArchetypeProduct> products) {
		return products.stream().collect(toMap(ArchetypeProduct::getId, ArchetypeProduct::getName));
	}

	private static List<String> names(List<ArchetypeProduct> products) {
		return products.stream().map(ArchetypeProduct::getName).toList();
	}

	private static ArchetypeCategoryEntity category(UUID id, String name) {
		var category = new ArchetypeCategoryEntity();
		category.setId(id);
		category.setLevel((short) 3);
		category.setName(name);
		return category;
	}

	private static ArchetypeProductEntity product(UUID id, ArchetypeCategoryEntity category, String name, String agbCode) {
		var product = new ArchetypeProductEntity();
		product.setId(id);
		product.setCategory(category);
		product.setName(name);
		product.setAgbCode(agbCode);
		product.setEnvironmentalImpact(new EnvironmentalImpact());
		product.setSocialImpact(new SocialImpact());
		product.setAnimalWelfareImpact(new AnimalWelfareImpact());
		product.setNutriScore("A");
		return product;
	}
}
