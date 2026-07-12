package eu.tealhelix.howibuy.services.v1.enrichment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import eu.tealhelix.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.tealhelix.howibuy.dao.FoodTermDao;
import eu.tealhelix.howibuy.services.model.FoodTerm;
import eu.tealhelix.howibuy.services.model.ImmutableFoodTerm;
import io.smallrye.mutiny.Uni;
import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@EnableAutoWeld
@AddBeanClasses(FoodTermGlossary.class)
@ExtendWith(MockitoExtension.class)
public class FoodTermGlossaryTest {
	private static final Duration WAIT = Duration.ofSeconds(300);

	@Produces
	@Mock
	FoodTermDao foodTermDao;

	@Produces
	@RegisterExtension
	MockReactivePersistenceContextFactory mockPersistenceContextFactory = new MockReactivePersistenceContextFactory();

	@Inject
	FoodTermGlossary sut;

	@Test
	void matchesTheProductNameAgainstTheGlossaryOfItsLanguage() {
		when(foodTermDao.retrieveByLanguage(any(), eq("el")))
				.thenReturn(Uni.createFrom().item(List.of(term("Ανθότυρος"), term("Βλήτα"))));

		var matches = sut.match("el", "Amari Ανθότυρος Ξηρός").await().atMost(WAIT);

		assertEquals(List.of("Ανθότυρος"), matches.stream().map(FoodTerm::getTerm).toList(), "only the term present in the name");
	}

	@Test
	void loadsEachLanguageGlossaryOnlyOnce() {
		when(foodTermDao.retrieveByLanguage(any(), eq("el")))
				.thenReturn(Uni.createFrom().item(List.of(term("Βλήτα"))));

		sut.match("el", "Βλήτα Ελληνικά").await().atMost(WAIT);
		sut.match("el", "Φρέσκα βλήτα").await().atMost(WAIT);

		verify(foodTermDao, times(1)).retrieveByLanguage(any(), eq("el"));
	}

	private static FoodTerm term(String term) {
		return ImmutableFoodTerm.builder().term(term).canonicalEn(term).description(term).build();
	}
}
