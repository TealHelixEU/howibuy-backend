package eu.tealhelix.howibuy.services.v1.classification.impl;

import static java.util.Comparator.comparing;
import static java.util.function.BinaryOperator.maxBy;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import jakarta.enterprise.context.ApplicationScoped;

import eu.tealhelix.common.types.Nullable;
import eu.tealhelix.howibuy.services.v1.classification.ClassificationResolution;
import eu.tealhelix.howibuy.services.v1.classification.Resolution;
import eu.tealhelix.howibuy.v1.model.ClassificationAttempt;
import eu.tealhelix.howibuy.v1.types.Classifier;

/**
 * Of the attempts made from the current input hash, the latest per classifier and language counts. The classifier
 * with the highest precedence among them decides: if its attempts agree, they resolve the Classification; if they
 * disagree, the one in the Retailer's classification language does, else the one with the first language code, and the
 * Classification needs review.
 */
@ApplicationScoped
public class ClassificationResolutionImpl implements ClassificationResolution {
	@Override
	public Optional<Resolution> resolve(String inputHash, @Nullable String classificationLanguage, List<ClassificationAttempt> attempts) {
		var latest = latestPerClassifierAndLanguage(inputHash, attempts);
		return latest.stream()
				.map(ClassificationAttempt::getClassifier)
				.min(Comparator.naturalOrder())
				.map(decidingClassifier -> decide(classificationLanguage, latest.stream()
						.filter(attempt -> attempt.getClassifier() == decidingClassifier)
						.sorted(comparing(ClassificationAttempt::getLanguage))
						.toList()));
	}

	private static Collection<ClassificationAttempt> latestPerClassifierAndLanguage(String inputHash, List<ClassificationAttempt> attempts) {
		return attempts.stream()
				.filter(attempt -> attempt.getInputHash().equals(inputHash))
				.collect(toMap(
						attempt -> new ClassifierAndLanguage(attempt.getClassifier(), attempt.getLanguage()),
						identity(),
						maxBy(comparing(ClassificationAttempt::getCreatedAt))
				))
				.values();
	}

	/**
	 * @param attempts The attempts of the deciding classifier, one per language, in the order of their language codes
	 */
	private static Resolution decide(@Nullable String classificationLanguage, List<ClassificationAttempt> attempts) {
		var agree = attempts.stream().map(ClassificationAttempt::getArchetypeProductId).distinct().count() == 1;
		if (agree) return new Resolution(attempts.getFirst().getArchetypeProductId(), false);
		var chosen = attempts.stream()
				.filter(attempt -> Objects.equals(attempt.getLanguage(), classificationLanguage))
				.findFirst()
				.orElse(attempts.getFirst());
		return new Resolution(chosen.getArchetypeProductId(), true);
	}

	private record ClassifierAndLanguage(Classifier classifier, String language) {
	}
}
