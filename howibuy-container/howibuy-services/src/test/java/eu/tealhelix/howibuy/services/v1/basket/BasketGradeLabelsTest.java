package eu.tealhelix.howibuy.services.v1.basket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import eu.tealhelix.howibuy.v1.types.BasketGrade;
import org.jboss.logmanager.LogContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class BasketGradeLabelsTest {
	/**
	 * A test bundle: English, and a Greek translation of {@link BasketGrade#A} only.
	 */
	private static final String INCOMPLETE_BUNDLE = BasketGradeLabels.class.getPackageName() + ".IncompleteBasketGradeLabels";

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
		var logger = LogContext.getLogContext().getLogger(BasketGradeLabels.class.getName());
		logger.addHandler(logHandler);
		logger.setUseParentHandlers(false);
	}

	@AfterEach
	void tearDown() {
		var logger = LogContext.getLogContext().getLogger(BasketGradeLabels.class.getName());
		logger.removeHandler(logHandler);
		logger.setUseParentHandlers(true);
	}

	@ParameterizedTest
	@EnumSource(BasketGrade.class)
	void everyGradeHasAnEnglishLabel(BasketGrade grade) {
		var sut = new BasketGradeLabels();

		assertFalse(sut.labelOf(grade, "en").isBlank());
		assertEquals(List.of(), logged);
	}

	@Test
	void answersTheTranslation() {
		var sut = new BasketGradeLabels(INCOMPLETE_BUNDLE);

		assertEquals("Καλό καλάθι", sut.labelOf(BasketGrade.A, "el"));
		assertEquals(List.of(), logged);
	}

	@Test
	void fallsBackToEnglishForAMissingTranslationWithAWarning() {
		var sut = new BasketGradeLabels(INCOMPLETE_BUNDLE);

		assertEquals("Fair basket", sut.labelOf(BasketGrade.B, "el"));
		assertEquals(1, logged.size());
		assertEquals(Level.WARNING, logged.getFirst().getLevel());
		assertEquals("Basket Grade label not translated, falling back to English, language and grade: el B",
				logged.getFirst().getMessage().replace("{}", "%s").formatted(logged.getFirst().getParameters()));
	}

	@Test
	void fallsBackToEnglishForAnUnsupportedLanguageSilently() {
		var sut = new BasketGradeLabels(INCOMPLETE_BUNDLE);

		assertEquals("Fair basket", sut.labelOf(BasketGrade.B, "fr"));
		assertEquals(List.of(), logged);
	}
}
