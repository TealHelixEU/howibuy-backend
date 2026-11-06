package eu.tealhelix.howibuy.services.v1.basket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import eu.tealhelix.howibuy.v1.types.BasketMatch;
import org.jboss.logmanager.LogContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class LabelsTest {
	/**
	 * A test bundle of {@link Grade}: English, and a Greek translation of {@link Grade#A} only.
	 */
	private static final String INCOMPLETE_BUNDLE = Labels.class.getPackageName() + ".IncompleteLabels";

	/**
	 * The enum labelled by the test bundle.
	 */
	private enum Grade {
		A, B, C
	}

	private final List<LogRecord> logged = new ArrayList<>();

	private final Handler logHandler = new Handler() {
		@Override
		public void publish(LogRecord record) {
			logged.add(record);
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() {
		}
	};

	@BeforeEach
	void setUp() {
		var logger = LogContext.getLogContext().getLogger(Labels.class.getName());
		logger.addHandler(logHandler);
		logger.setUseParentHandlers(false);
	}

	@AfterEach
	void tearDown() {
		var logger = LogContext.getLogContext().getLogger(Labels.class.getName());
		logger.removeHandler(logHandler);
		logger.setUseParentHandlers(true);
	}

	@ParameterizedTest
	@EnumSource(BasketMatch.class)
	void everyBasketMatchHasAnEnglishLabel(BasketMatch basketMatch) {
		var sut = new Labels<BasketMatch>(Labels.class.getPackageName() + ".BasketMatchLabels");

		assertFalse(sut.labelOf(basketMatch, "en").isBlank());
		assertEquals(List.of(), logged);
	}

	@Test
	void answersTheTranslation() {
		var sut = new Labels<Grade>(INCOMPLETE_BUNDLE);

		assertEquals("Καλό καλάθι", sut.labelOf(Grade.A, "el"));
		assertEquals(List.of(), logged);
	}

	@Test
	void fallsBackToEnglishForAMissingTranslationWithAWarning() {
		var sut = new Labels<Grade>(INCOMPLETE_BUNDLE);

		assertEquals("Fair basket", sut.labelOf(Grade.B, "el"));
		assertEquals(1, logged.size());
		assertEquals(Level.WARNING, logged.getFirst().getLevel());
		assertEquals("Label not translated, falling back to English, bundle, language and key: " + INCOMPLETE_BUNDLE + " el B",
				logged.getFirst().getMessage().replace("{}", "%s").formatted(logged.getFirst().getParameters()));
	}

	@Test
	void fallsBackToEnglishForAnUnsupportedLanguageSilently() {
		var sut = new Labels<Grade>(INCOMPLETE_BUNDLE);

		assertEquals("Fair basket", sut.labelOf(Grade.B, "fr"));
		assertEquals(List.of(), logged);
	}
}
