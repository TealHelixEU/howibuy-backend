package eu.tealhelix.howibuy.services.v1.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import eu.tealhelix.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.tealhelix.howibuy.dao.ArchetypeProductDao;
import eu.tealhelix.howibuy.dao.SubstitutabilityDao;
import eu.tealhelix.howibuy.scoring.v1.ScientificWeights;
import eu.tealhelix.howibuy.services.model.ArchetypeProductImpacts;
import eu.tealhelix.howibuy.services.model.ImmutableArchetypeProductImpacts;
import eu.tealhelix.howibuy.services.model.ImmutableSubstitutability;
import eu.tealhelix.howibuy.v1.types.AlternativeForProductType;
import eu.tealhelix.howibuy.v1.types.ArchetypeCategoryId;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.impl.ArchetypeCategoryIdImpl;
import eu.tealhelix.howibuy.v1.types.impl.ArchetypeProductIdImpl;
import io.smallrye.mutiny.Uni;
import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@EnableAutoWeld
@AddBeanClasses(ArchetypeCorpus.class)
@ExtendWith(MockitoExtension.class)
public class ArchetypeCorpusTest {
	private static final Duration WAIT = Duration.ofSeconds(300);

	private static final ArchetypeCategoryId L2_JUICES = new ArchetypeCategoryIdImpl("00000000-0000-0000-0000-000000000011");
	private static final ArchetypeProductId ORANGE_JUICE = new ArchetypeProductIdImpl("00000000-0000-0000-0000-000000000201");

	@Produces
	@Mock
	ArchetypeProductDao archetypeProductDao;

	@Produces
	@Mock
	SubstitutabilityDao substitutabilityDao;

	@Produces
	@RegisterExtension
	private MockReactivePersistenceContextFactory mockPersistenceContextFactory = new MockReactivePersistenceContextFactory();

	@Inject
	ArchetypeCorpus sut;

	@Test
	void readsTheCorpusAndTheMatrixOnceHoweverOftenItIsAsked() {
		mockCorpus();

		var first = sut.scoredArchetypes().await().atMost(WAIT);
		var second = sut.scoredArchetypes().await().atMost(WAIT);

		assertSame(first, second, "the corpus is reference data: reading and scoring it again would give the same answer");
		verify(archetypeProductDao, times(1)).retrieveAllWithImpacts(any());
		verify(substitutabilityDao, times(1)).retrieveAll(any());
	}

	@Test
	void answersFromTheScoredCorpusItLoaded() {
		mockCorpus();

		var recommendations = sut.scoredArchetypes().await().atMost(WAIT)
				.recommendationsFor(ORANGE_JUICE, ScientificWeights.profile());

		assertEquals(AlternativeForProductType.GOOD_ENOUGH, recommendations.scientific().getType(),
				"the only product of its category, so it is its own best alternative");
		assertEquals("Orange juice", recommendations.scientific().getName());
	}

	@Test
	void readsAgainAfterAFailedLoadRatherThanRememberingTheFailure() {
		when(archetypeProductDao.retrieveAllWithImpacts(any()))
				.thenReturn(Uni.createFrom().failure(new IllegalStateException("The database is having a moment")))
				.thenReturn(Uni.createFrom().item(List.of(orangeJuice())));
		when(substitutabilityDao.retrieveAll(any()))
				.thenReturn(Uni.createFrom().item(List.of(ImmutableSubstitutability.builder()
						.fromCategoryId(L2_JUICES).toCategoryId(L2_JUICES).degree((short) 5).build())));

		assertThrows(IllegalStateException.class, () -> sut.scoredArchetypes().await().atMost(WAIT),
				"the first load fails, as arranged");
		var recovered = sut.scoredArchetypes().await().atMost(WAIT);

		assertNotNull(recovered, "a load that failed is not the answer for the rest of the application's life");
		verify(archetypeProductDao, times(2)).retrieveAllWithImpacts(any());
	}

	private void mockCorpus() {
		when(archetypeProductDao.retrieveAllWithImpacts(any())).thenReturn(Uni.createFrom().item(List.of(orangeJuice())));
		when(substitutabilityDao.retrieveAll(any())).thenReturn(Uni.createFrom().item(List.of(
				ImmutableSubstitutability.builder()
						.fromCategoryId(L2_JUICES).toCategoryId(L2_JUICES).degree((short) 5).build())));
	}

	private static ArchetypeProductImpacts orangeJuice() {
		return ImmutableArchetypeProductImpacts.builder()
				.id(ORANGE_JUICE)
				.name("Orange juice")
				.agbCode("agb-oj")
				.l2CategoryId(L2_JUICES)
				.indicatorValues(Map.of())
				.nutriScore("Nutriscore_A")
				.build();
	}
}
