package eu.tealhelix.common.types;

/**
 * Interface for values that can be represented as strings.
 */
public interface RepresentableAsString {
	/**
	 * Provides the string representation of this object, to be used for marshalling.
	 * In contrast, the {@code toString} method should be used to represent this object in
	 * a (fairly) human-readable manner.
	 *
	 * @return The string representation of this object
	 */
	String asString();
}
