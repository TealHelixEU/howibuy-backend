package eu.tealhelix.howibuy.jaxrs.v1;

public interface HowiBuyOpenApiTagNames {
	String PRODUCT_ASSESSMENT = "Product assessment";
	String PRODUCT_ASSESSMENT_DESC = "Assessment of a product's sustainability and of the alternatives to it that score better. Every assessment is made for the authenticated end-user, against their own weighting of the sustainability dimensions (i.e. the SFC), if they exist.";

	String HANDOFF = "Session handoff";
	String HANDOFF_DESC = "Carrying a user's session from a retailer's application over to the single-page application: the retailer is handed a ticket, the single-page application redeems it for a session of its own, and renews that session's token for as long as the user keeps working.";

	String TOKEN_EXCHANGE = "Token exchange";
	String TOKEN_EXCHANGE_DESC = "Exchange of a retailer's service-account token for one that acts as a user of that retailer, for a retailer calling this application on a user's behalf.";
}
