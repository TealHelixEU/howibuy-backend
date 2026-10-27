package eu.tealhelix.cataloguesync;

import java.util.OptionalInt;
import java.util.concurrent.Callable;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

@Command(name = "classify", mixinStandardHelpOptions = true,
		description = "Has HowiBuy's LLM classify the products waiting to be classified, in each language they are named in. It prints each attempt that fails as it fails, and at the end the products and attempts made, their outcomes, the number of failed attempts and the time it took. A failed attempt is skipped; HowiBuy refusing the Catalogue Sync, or any error reading the pending products, stops it. Running it again goes on with the products still pending.",
		exitCodeListHeading = "Exit codes:%n",
		exitCodeList = {
				"0:No attempt failed.",
				"1:The classification stopped.",
				"2:The options are invalid.",
				"3:Some attempts failed.",
				"4:Every attempt failed."
		})
public class ClassifyCommand implements Callable<Integer> {
	static final int SOME_ATTEMPTS_FAILED = 3;
	static final int EVERY_ATTEMPT_FAILED = 4;

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
	public Integer call() {
		var products = limit != null ? limit : configuredLimit;
		var attemptsPerMinute = rate != null ? OptionalInt.of(rate) : configuredRate;
		if (products < 1) {
			throw new ParameterException(spec.commandLine(), "--limit must be at least 1, limit: " + products);
		}
		if (attemptsPerMinute.isPresent() && attemptsPerMinute.getAsInt() < 1) {
			throw new ParameterException(spec.commandLine(), "--rate must be at least 1, rate: " + attemptsPerMinute.getAsInt());
		}
		var out = spec.commandLine().getOut();
		var result = productClassification.classify(products, attemptsPerMinute, failure -> {
			out.printf("Failed, retailerProductId: %s, language: %s, error: %s%n", failure.retailerProductId(), failure.language(), failure.error());
			out.flush();
		});
		var elapsed = result.elapsed();
		out.printf(
				"The classification ended, products: %d, attempts: %d, matched: %d, no match: %d, malformed replies: %d, failures: %d, elapsed: %d:%02d:%02d%n",
				result.products(), result.attempts(), result.matched(), result.noMatch(), result.malformedReplies(), result.failures(),
				elapsed.toHours(), elapsed.toMinutesPart(), elapsed.toSecondsPart()
		);
		if (result.failures() == 0) {
			return CommandLine.ExitCode.OK;
		}
		return result.failures() == result.attempts() ? EVERY_ATTEMPT_FAILED : SOME_ATTEMPTS_FAILED;
	}
}
