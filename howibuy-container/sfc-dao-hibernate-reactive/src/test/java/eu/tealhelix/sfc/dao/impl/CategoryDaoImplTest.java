package eu.tealhelix.sfc.dao.impl;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import eu.tealhelix.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.tealhelix.common.test.jpa.HibernateReactiveExtension;
import eu.tealhelix.common.test.liquibase.LiquibaseExtension;
import eu.tealhelix.sfc.dao.jpa.CategoryEntity;
import eu.tealhelix.sfc.dao.jpa.CategoryTextEntity;
import eu.tealhelix.sfc.v1.model.Category;
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
 * The category read against a real database: the {@code (category, text)} join is resolved for the requested language,
 * the optional links map through (present and absent), and categories come back ordered by dimension. The schema is
 * created from the SFC changelog (without the {@code appdata} seed) and a small controlled fixture is inserted here.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Testcontainers
public class CategoryDaoImplTest {
	private static final Duration WAIT = Duration.ofSeconds(300);

	private static final UUID ECOLOGICAL_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID HEALTH_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

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

	private final CategoryDaoImpl sut = new CategoryDaoImpl();

	@Test
	@Order(1)
	void seed(Mutiny.SessionFactory sessionFactory) {
		var ecological = category(ECOLOGICAL_ID, SustainabilityDimension.ECOLOGICAL);
		var health = category(HEALTH_ID, SustainabilityDimension.HEALTH);
		factory(sessionFactory).withTransaction(tx ->
				tx.persistAll(ecological, health)
						.chain(() -> tx.flush())
						.chain(() -> tx.persistAll(
								categoryText(ecological, "en", "Ecological", "Ecological, in English", null, null),
								categoryText(ecological, "el", "Οικολογική", "Οικολογική, στα ελληνικά", null, null),
								categoryText(health, "en", "Health", "Health, in English", "https://video/health", "https://detail/health"),
								categoryText(health, "el", "Υγεία", "Υγεία, στα ελληνικά", null, null)))
		).await().atMost(WAIT);
	}

	@Test
	@Order(2)
	void retrievesCategoriesForTheRequestedLanguageOrderedByDimension(Mutiny.SessionFactory sessionFactory) {
		var categories = factory(sessionFactory).withoutTransaction(em -> sut.retrieveByLanguage(em, "en")).await().atMost(WAIT);

		assertEquals(
				List.of(SustainabilityDimension.ECOLOGICAL, SustainabilityDimension.HEALTH),
				categories.stream().map(Category::getDimension).toList(),
				"ordered by dimension");
		assertEquals(
				List.of("Ecological", "Health"),
				categories.stream().map(Category::getName).toList(),
				"names resolved for English");
	}

	@Test
	@Order(3)
	void resolvesTextToTheRequestedLanguage(Mutiny.SessionFactory sessionFactory) {
		var categories = factory(sessionFactory).withoutTransaction(em -> sut.retrieveByLanguage(em, "el")).await().atMost(WAIT);

		assertEquals(
				List.of("Οικολογική", "Υγεία"),
				categories.stream().map(Category::getName).toList(),
				"names resolved for Greek");
	}

	@Test
	@Order(4)
	void mapsTheOptionalLinksIncludingWhenAbsent(Mutiny.SessionFactory sessionFactory) {
		var byDimension = factory(sessionFactory).withoutTransaction(em -> sut.retrieveByLanguage(em, "en")).await().atMost(WAIT)
				.stream().collect(toMap(Category::getDimension, identity()));

		var ecological = byDimension.get(SustainabilityDimension.ECOLOGICAL);
		assertNull(ecological.getVideoUrl(), "no video link authored");
		assertNull(ecological.getDetailUrl(), "no detail link authored");

		var health = byDimension.get(SustainabilityDimension.HEALTH);
		assertEquals("https://video/health", health.getVideoUrl());
		assertEquals("https://detail/health", health.getDetailUrl());
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

	private static CategoryTextEntity categoryText(CategoryEntity category, String lang, String name, String description, String videoUrl, String detailUrl) {
		var text = new CategoryTextEntity();
		text.setCategory(category);
		text.setLang(lang);
		text.setName(name);
		text.setDescription(description);
		text.setVideoUrl(videoUrl);
		text.setDetailUrl(detailUrl);
		return text;
	}
}
