package eu.tealhelix.betterme.v1.model;

public interface UserProfileData {
	String getExternalId();
	String getUsername();
	String getEmail();
	Boolean getEmailConsent();
}
