package eu.tealhelix.sfc.dao.jpa;

import static jakarta.persistence.FetchType.LAZY;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * The per-language text of a {@link CategoryEntity}: one row per {@code (category, language)}, which together form the
 * primary key.
 */
@Entity
@IdClass(CategoryTextEntityId.class)
@Table(name = "TH_SFC_CATEGORY_TEXT")
public class CategoryTextEntity {
	@Id
	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "category_id")
	private CategoryEntity category;

	@Id
	@Column(name = "lang")
	private String lang;

	@Column(name = "name")
	private String name;

	@Column(name = "description")
	private String description;

	@Column(name = "video_url")
	private String videoUrl;

	@Column(name = "detail_url")
	private String detailUrl;

	public CategoryEntity getCategory() {
		return category;
	}

	public void setCategory(CategoryEntity category) {
		this.category = category;
	}

	public String getLang() {
		return lang;
	}

	public void setLang(String lang) {
		this.lang = lang;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getVideoUrl() {
		return videoUrl;
	}

	public void setVideoUrl(String videoUrl) {
		this.videoUrl = videoUrl;
	}

	public String getDetailUrl() {
		return detailUrl;
	}

	public void setDetailUrl(String detailUrl) {
		this.detailUrl = detailUrl;
	}
}
