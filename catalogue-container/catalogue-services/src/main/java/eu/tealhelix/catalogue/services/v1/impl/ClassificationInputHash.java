package eu.tealhelix.catalogue.services.v1.impl;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

/**
 * The classification input hash of a Retailer Product: the SHA-256 of what its Classification is made from, its names,
 * characteristics and tags. A product whose hash did not change need not be classified again, so the hash depends on
 * nothing that a Classification ignores: neither the order of the names, characteristics and tags, nor the whitespace
 * around them, nor how their Unicode is composed, nor repeated tags. The price never takes part.
 */
public final class ClassificationInputHash {
	private ClassificationInputHash() {
	}

	/**
	 * @param names           The names of the product in all its languages, by language code
	 * @param characteristics The characteristics of the product
	 * @param tags            The tags of the product
	 * @return The hash, in lowercase hex
	 */
	public static String of(Map<String, String> names, Map<String, String> characteristics, Collection<String> tags) {
		var digest = sha256();
		update(digest, canonical(names));
		update(digest, canonical(characteristics));
		update(digest, tags.stream().map(ClassificationInputHash::canonical).distinct().sorted().toList());
		return HexFormat.of().formatHex(digest.digest());
	}

	/**
	 * The entries as a flat list of keys and values, sorted by key and then by value, because two keys may become equal
	 * once canonical.
	 */
	private static List<String> canonical(Map<String, String> map) {
		return map.entrySet().stream()
				.map(e -> Map.entry(canonical(e.getKey()), canonical(e.getValue())))
				.sorted(Entry.<String, String>comparingByKey().thenComparing(Entry.comparingByValue()))
				.flatMap(e -> Stream.of(e.getKey(), e.getValue()))
				.toList();
	}

	private static String canonical(String value) {
		return Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
	}

	/**
	 * Every part is preceded by its length, so the parts cannot run into each other: {@code ["ab", "c"]} differs from
	 * {@code ["a", "bc"]}, and the names from the characteristics.
	 */
	private static void update(MessageDigest digest, List<String> values) {
		digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(values.size()).array());
		for (var value : values) {
			var bytes = value.getBytes(UTF_8);
			digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
			digest.update(bytes);
		}
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("Every Java platform supports SHA-256", e);
		}
	}
}
