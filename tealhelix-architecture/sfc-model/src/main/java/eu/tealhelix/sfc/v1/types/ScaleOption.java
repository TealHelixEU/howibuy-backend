package eu.tealhelix.sfc.v1.types;

/**
 * The fixed five-point scale on which every compass question is answered: how important the facet is to the user. The
 * points are ordered from {@link #NOT_IMPORTANT least} to {@link #EXTREMELY_IMPORTANT most} important; each carries the
 * ordinal (1–5) stored for an answer. The localized labels shown to the user live in a resource bundle keyed by this
 * enum, not here.
 */
public enum ScaleOption {
	NOT_IMPORTANT(1),
	SLIGHTLY_IMPORTANT(2),
	MODERATELY_IMPORTANT(3),
	VERY_IMPORTANT(4),
	EXTREMELY_IMPORTANT(5);

	private final short value;

	ScaleOption(int value) {
		this.value = (short) value;
	}

	/**
	 * The 1–5 ordinal stored for an answer picking this option.
	 */
	public short getValue() {
		return value;
	}
}
