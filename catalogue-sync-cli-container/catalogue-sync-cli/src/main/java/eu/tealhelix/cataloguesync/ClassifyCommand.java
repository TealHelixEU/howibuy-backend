package eu.tealhelix.cataloguesync;

import java.util.OptionalInt;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

@Command(name = "classify", mixinStandardHelpOptions = true,
		description = "Has HowiBuy's LLM classify the products waiting to be classified, in each language they are named in, and prints the products and attempts made, their outcomes, the attempts that failed and the time it took. A failed attempt is skipped; HowiBuy refusing the Catalogue Sync, or any error reading the pending products, stops it with a non-zero exit code. Running it again goes on with the products still pending.")
public class ClassifyCommand implements Runnable {
	@Inject
	ProductClassification productClassification;

	@ConfigProperty(name = "catalogue-sync.classification.limit")
	int configuredLimit;

	@ConfigProperty(name = "catalogue-sync.classification.rate")
	OptionalInt configuredRate;

	@Option(names = "--limit", paramLabel = "PRODUCTS",
			description = "How many products to classify at most; by default catalogue-sync.classification.limit.")
	Integer limit;

	@Option(names = "--rate", paramLabel = "ATTEMPTS",
			description = "How many attempts to start in a minute at most; by default catalogue-sync.classification.rate, and as fast as HowiBuy answers when that is not set either.")
	Integer rate;

	@Spec
	CommandSpec spec;

	@Override
	public void run() {
		var products = limit != null ? limit : configuredLimit;
		var attemptsPerMinute = rate != null ? OptionalInt.of(rate) : configuredRate;
		if (products < 1) {
			throw new ParameterException(spec.commandLine(), "--limit must be at least 1, limit: " + products);
		}
		if (attemptsPerMinute.isPresent() && attemptsPerMinute.getAsInt() < 1) {
			throw new ParameterException(spec.commandLine(), "--rate must be at least 1, rate: " + attemptsPerMinute.getAsInt());
		}
		var result = productClassification.classify(products, attemptsPerMinute);
		var out = spec.commandLine().getOut();
		var elapsed = result.elapsed();
		out.printf(
				"The classification ended, products: %d, attempts: %d, matched: %d, no match: %d, malformed replies: %d, failures: %d, elapsed: %d:%02d:%02d%n",
				result.products(), result.attempts(), result.matched(), result.noMatch(), result.malformedReplies(), result.failures().size(),
				elapsed.toHours(), elapsed.toMinutesPart(), elapsed.toSecondsPart()
		);
		result.failures().forEach(failure -> out.printf("Failed, retailerProductId: %s, language: %s, error: %s%n", failure.retailerProductId(), failure.language(), failure.error()));
	}
}
