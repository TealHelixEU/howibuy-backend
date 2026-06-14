package eu.tealhelix.howibuy.v1.model;

public interface UserProfileData {
	String getExternalId();
	String getUsername();
	String getEmail();
	Boolean getEmailConsent();
}
