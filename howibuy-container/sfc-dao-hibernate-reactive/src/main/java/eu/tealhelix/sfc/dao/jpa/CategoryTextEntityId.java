package eu.tealhelix.sfc.dao.jpa;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite identifier of a {@link CategoryTextEntity}: the category it localizes together with its language.
 */
public class CategoryTextEntityId implements Serializable {
	private UUID category;
	private String lang;

	public CategoryTextEntityId() {
	}

	public CategoryTextEntityId(UUID category, String lang) {
		this.category = category;
		this.lang = lang;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof CategoryTextEntityId that)) return false;
		return Objects.equals(category, that.category) && Objects.equals(lang, that.lang);
	}

	@Override
	public int hashCode() {
		return Objects.hash(category, lang);
	}
}
