package eu.tealhelix.catalogue.services.v1.impl;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import eu.tealhelix.catalogue.feeds.DeletionRecord;
import eu.tealhelix.catalogue.feeds.FeedRecord;
import eu.tealhelix.catalogue.v1.model.Catalogue;

/**
 * The rules every record of every Feed is held to, whichever adapter read it. A record that passes them can be stored,
 * and every active Listing has a name in the primary language of its Catalogue to fall back to. A blank value counts as
 * missing. Missing characteristics and tags count as none. A record of a Deletion Feed carries the product key alone,
 * which is held to the same rules.
 */
public final class FeedRecordValidator {
	static final int MAX_PRODUCT_KEY_LENGTH = 255;
	private static final int MAX_NAME_LENGTH = 512;
	private static final int MAX_PRICE_INTEGER_DIGITS = 8;
	private static final int MAX_PRICE_DECIMAL_DIGITS = 4;
	private static final Set<String> CURRENCY_CODES = Currency.getAvailableCurrencies().stream().map(Currency::getCurrencyCode).collect(Collectors.toSet());

	private FeedRecordValidator() {
	}

	/**
	 * @param record    The record
	 * @param catalogue The Catalogue of the Feed
	 * @return The first rule the record breaks, as the reason to reject it; empty if it breaks none
	 */
	public static Optional<String> findFault(FeedRecord record, Catalogue catalogue) {
		return findProductKeyFault(record.productKey())
				.or(() -> findNamesFault(record.names(), catalogue))
				.or(() -> findPriceFault(record.price()))
				.or(() -> findCurrencyFault(record.currency()))
				.or(() -> findCharacteristicsFault(record.characteristics()))
				.or(() -> findTagsFault(record.tags()));
	}

	/**
	 * @param record The record of a Deletion Feed
	 * @return The first rule the record breaks, as the reason to reject it; empty if it breaks none
	 */
	public static Optional<String> findFault(DeletionRecord record) {
		return findProductKeyFault(record.productKey());
	}

	private static Optional<String> findProductKeyFault(String productKey) {
		if (isBlank(productKey)) {
			return Optional.of("The product key is missing");
		} else if (productKey.length() > MAX_PRODUCT_KEY_LENGTH) {
			return Optional.of("The product key is longer than " + MAX_PRODUCT_KEY_LENGTH + " characters");
		}
		return Optional.empty();
	}

	private static Optional<String> findNamesFault(Map<String, String> names, Catalogue catalogue) {
		if (names == null || isBlank(names.get(catalogue.getPrimaryLanguage()))) {
			return Optional.of("There is no name in the primary language of the Catalogue, language: " + catalogue.getPrimaryLanguage());
		}
		for (var name : names.entrySet()) {
			if (!catalogue.getAcceptedLanguages().contains(name.getKey())) {
				return Optional.of("The Catalogue does not accept the language, language: " + name.getKey());
			} else if (isBlank(name.getValue())) {
				return Optional.of("A name is blank, language: " + name.getKey());
			} else if (name.getValue().length() > MAX_NAME_LENGTH) {
				return Optional.of("A name is longer than " + MAX_NAME_LENGTH + " characters, language: " + name.getKey());
			}
		}
		return Optional.empty();
	}

	/**
	 * The price must also fit the column that stores it, so that it is stored exactly as the Feed tells it.
	 */
	private static Optional<String> findPriceFault(BigDecimal price) {
		if (price == null) {
			return Optional.of("The price is missing");
		} else if (price.signum() <= 0) {
			return Optional.of("The price is not positive, price: " + price.toPlainString());
		}
		var significant = price.stripTrailingZeros();
		if (significant.scale() > MAX_PRICE_DECIMAL_DIGITS || significant.precision() - significant.scale() > MAX_PRICE_INTEGER_DIGITS) {
			return Optional.of("The price has more than " + MAX_PRICE_INTEGER_DIGITS + " integer or " + MAX_PRICE_DECIMAL_DIGITS
					+ " decimal digits, price: " + price.toPlainString());
		}
		return Optional.empty();
	}

	private static Optional<String> findCurrencyFault(String currency) {
		if (isBlank(currency)) {
			return Optional.of("The currency is missing");
		} else if (!CURRENCY_CODES.contains(currency)) {
			return Optional.of("The currency is not an ISO 4217 code, currency: " + currency);
		}
		return Optional.empty();
	}

	private static Optional<String> findCharacteristicsFault(Map<String, String> characteristics) {
		if (characteristics == null) return Optional.empty();
		for (var characteristic : characteristics.entrySet()) {
			if (isBlank(characteristic.getKey())) {
				return Optional.of("A characteristic has no name");
			} else if (isBlank(characteristic.getValue())) {
				return Optional.of("A characteristic has no value, characteristic: " + characteristic.getKey());
			}
		}
		return Optional.empty();
	}

	private static Optional<String> findTagsFault(List<String> tags) {
		if (tags != null && tags.stream().anyMatch(FeedRecordValidator::isBlank)) {
			return Optional.of("A tag is blank");
		}
		return Optional.empty();
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
