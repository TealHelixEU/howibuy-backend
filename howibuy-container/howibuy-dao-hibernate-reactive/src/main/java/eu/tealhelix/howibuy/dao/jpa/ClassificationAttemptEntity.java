package eu.tealhelix.howibuy.dao.jpa;

import static jakarta.persistence.EnumType.STRING;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import eu.tealhelix.howibuy.v1.types.Classifier;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "TH_CLASSIFICATION_ATTEMPT")
public class ClassificationAttemptEntity {
	@Id
	@Column(name = "id")
	private UUID id;

	@Column(name = "retailer_product_id")
	private UUID retailerProductId;

	@Column(name = "lang")
	private String lang;

	@Column(name = "classifier")
	@Enumerated(STRING)
	private Classifier classifier;

	@Column(name = "classifier_version")
	private String classifierVersion;

	@Column(name = "archetype_product_id")
	private UUID archetypeProductId;

	@Column(name = "input_hash")
	private String inputHash;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "diagnostics")
	private Map<String, String> diagnostics;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getRetailerProductId() {
		return retailerProductId;
	}

	public void setRetailerProductId(UUID retailerProductId) {
		this.retailerProductId = retailerProductId;
	}

	public String getLang() {
		return lang;
	}

	public void setLang(String lang) {
		this.lang = lang;
	}

	public Classifier getClassifier() {
		return classifier;
	}

	public void setClassifier(Classifier classifier) {
		this.classifier = classifier;
	}

	public String getClassifierVersion() {
		return classifierVersion;
	}

	public void setClassifierVersion(String classifierVersion) {
		this.classifierVersion = classifierVersion;
	}

	public UUID getArchetypeProductId() {
		return archetypeProductId;
	}

	public void setArchetypeProductId(UUID archetypeProductId) {
		this.archetypeProductId = archetypeProductId;
	}

	public String getInputHash() {
		return inputHash;
	}

	public void setInputHash(String inputHash) {
		this.inputHash = inputHash;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public Map<String, String> getDiagnostics() {
		return diagnostics;
	}

	public void setDiagnostics(Map<String, String> diagnostics) {
		this.diagnostics = diagnostics;
	}
}
