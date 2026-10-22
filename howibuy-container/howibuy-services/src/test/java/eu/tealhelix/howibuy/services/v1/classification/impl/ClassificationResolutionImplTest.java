package eu.tealhelix.howibuy.services.v1.classification.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import eu.tealhelix.howibuy.services.v1.classification.Resolution;
import eu.tealhelix.howibuy.v1.model.ClassificationAttempt;
import eu.tealhelix.howibuy.v1.model.ImmutableClassificationAttempt;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.Classifier;
import eu.tealhelix.howibuy.v1.types.impl.ArchetypeProductIdImpl;
import org.junit.jupiter.api.Test;

class ClassificationResolutionImplTest {
	private static final String HASH = "h2";
	private static final String STALE_HASH = "h1";
	private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 22, 12, 0);

	private static final ArchetypeProductId YOGHURT = new ArchetypeProductIdImpl("1b0a5a3e-4f7e-4d3f-9a51-0c2b6f1e7a01");
	private static final ArchetypeProductId FETA = new ArchetypeProductIdImpl("1b0a5a3e-4f7e-4d3f-9a51-0c2b6f1e7a02");
	private static final ArchetypeProductId HONEY = new ArchetypeProductIdImpl("1b0a5a3e-4f7e-4d3f-9a51-0c2b6f1e7a03");

	private final ClassificationResolutionImpl sut = new ClassificationResolutionImpl();

	@Test
	void manualBeatsExpert() {
		assertResolution(YOGHURT, false, null,
				attempt(Classifier.EXPERT, "el", FETA, 0),
				attempt(Classifier.MANUAL, "el", YOGHURT, 1));
	}

	@Test
	void manualBeatsTheLlm() {
		assertResolution(YOGHURT, false, null,
				attempt(Classifier.MANUAL, "el", YOGHURT, 0),
				attempt(Classifier.LLM, "el", FETA, 1));
	}

	@Test
	void expertBeatsTheLlm() {
		assertResolution(YOGHURT, false, null,
				attempt(Classifier.LLM, "en", FETA, 0),
				attempt(Classifier.EXPERT, "el", YOGHURT, 1));
	}

	@Test
	void attemptsInDifferentLanguagesThatAgreeResolve() {
		assertResolution(YOGHURT, false, "el",
				attempt(Classifier.LLM, "el", YOGHURT, 0),
				attempt(Classifier.LLM, "en", YOGHURT, 1));
	}

	@Test
	void attemptsThatAgreeNoArchetypeMatchesResolveToNone() {
		assertResolution(null, false, "el",
				attempt(Classifier.LLM, "el", null, 0),
				attempt(Classifier.LLM, "en", null, 1));
	}

	@Test
	void aDisagreementResolvesToTheRetailersLanguageForReview() {
		assertResolution(FETA, true, "en",
				attempt(Classifier.LLM, "el", YOGHURT, 0),
				attempt(Classifier.LLM, "en", FETA, 1));
	}

	@Test
	void aDisagreementWithoutAnAttemptInTheRetailersLanguageResolvesByLanguageCode() {
		assertResolution(FETA, true, "lt",
				attempt(Classifier.LLM, "en", YOGHURT, 0),
				attempt(Classifier.LLM, "el", FETA, 1));
	}

	@Test
	void aDisagreementWithoutARetailerLanguageResolvesByLanguageCode() {
		assertResolution(FETA, true, null,
				attempt(Classifier.LLM, "en", YOGHURT, 0),
				attempt(Classifier.LLM, "el", FETA, 1));
	}

	@Test
	void anArchetypeAndNoArchetypeDisagree() {
		assertResolution(null, true, "el",
				attempt(Classifier.LLM, "el", null, 0),
				attempt(Classifier.LLM, "en", YOGHURT, 1));
	}

	@Test
	void aDisagreementBelowTheDecidingClassifierNeedsNoReview() {
		assertResolution(HONEY, false, "el",
				attempt(Classifier.LLM, "el", YOGHURT, 0),
				attempt(Classifier.LLM, "en", FETA, 1),
				attempt(Classifier.EXPERT, "el", HONEY, 2));
	}

	@Test
	void attemptsFromAnotherInputHashAreIgnored() {
		assertResolution(FETA, false, "el",
				attempt(Classifier.MANUAL, "el", YOGHURT, STALE_HASH, 0),
				attempt(Classifier.LLM, "el", FETA, 1));
	}

	@Test
	void onlyTheLatestAttemptPerClassifierAndLanguageCounts() {
		assertResolution(FETA, false, "el",
				attempt(Classifier.MANUAL, "el", FETA, 1),
				attempt(Classifier.MANUAL, "el", YOGHURT, 0));
	}

	@Test
	void withoutAttemptsFromTheCurrentInputHashThereIsNoResolution() {
		assertEquals(Optional.empty(), sut.resolve(HASH, "el", List.of(attempt(Classifier.MANUAL, "el", YOGHURT, STALE_HASH, 0))));
	}

	private void assertResolution(ArchetypeProductId archetypeProductId, boolean needsReview, String classificationLanguage, ClassificationAttempt... attempts) {
		assertEquals(Optional.of(new Resolution(archetypeProductId, needsReview)), sut.resolve(HASH, classificationLanguage, List.of(attempts)));
	}

	private static ClassificationAttempt attempt(Classifier classifier, String language, ArchetypeProductId archetypeProductId, int minutesLater) {
		return attempt(classifier, language, archetypeProductId, HASH, minutesLater);
	}

	private static ClassificationAttempt attempt(Classifier classifier, String language, ArchetypeProductId archetypeProductId, String inputHash, int minutesLater) {
		return ImmutableClassificationAttempt.builder()
				.language(language)
				.classifier(classifier)
				.classifierVersion("test")
				.archetypeProductId(archetypeProductId)
				.inputHash(inputHash)
				.createdAt(T0.plusMinutes(minutesLater))
				.build();
	}
}
