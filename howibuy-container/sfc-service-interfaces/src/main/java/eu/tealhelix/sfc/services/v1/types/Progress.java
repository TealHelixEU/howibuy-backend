package eu.tealhelix.sfc.services.v1.types;

/**
 * How far a user has got — the number of questions {@link #answered} out of the {@link #total}, and that as a
 * {@link #percentage} rounded to the nearest whole number. Reported both for the whole compass and per category. An
 * empty question set is 0%.
 */
public record Progress(int answered, int total, int percentage) {
	public static Progress of(int answered, int total) {
		return new Progress(answered, total, total == 0 ? 0 : Math.round(100f * answered / total));
	}
}
