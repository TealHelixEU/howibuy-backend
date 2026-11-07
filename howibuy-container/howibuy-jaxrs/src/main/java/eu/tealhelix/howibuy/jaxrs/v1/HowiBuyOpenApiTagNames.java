package eu.tealhelix.howibuy.jaxrs.v1;

public interface HowiBuyOpenApiTagNames {
	String PRODUCT_ASSESSMENT = "Product assessment";
	String PRODUCT_ASSESSMENT_DESC = "Assessment of a product's sustainability and of the alternatives to it that score better. Every assessment is made for the authenticated end-user, against their own weighting of the sustainability dimensions (i.e. the SFC), if they exist.";

	String BASKET_ASSESSMENT = "Basket assessment";
	String BASKET_ASSESSMENT_DESC = "Assessment of a basket's sustainability as a whole, before and after the purchase. Every assessment is made for an end-user, the authenticated one or the one the retailer names, against their own weighting of the sustainability dimensions (i.e. the SFC), if they exist.";

	String DASHBOARD = "Dashboard";
	String DASHBOARD_DESC = "Where an end-user stands with their purchases: their experience points, their streak, their progress from month to month in each sustainability dimension, and their badges. For the end-user themselves.";

	String HANDOFF = "Session handoff";
	String HANDOFF_DESC = "Carrying a user's session from a retailer's application over to the single-page application: the retailer is handed a ticket, the single-page application redeems it for a session of its own, and renews that session's token for as long as the user keeps working.";

	String CATALOGUE_CHANGES = "Catalogue changes";
	String CATALOGUE_CHANGES_DESC = "The receiving end of the Product Changes that the Catalogue Sync carries over from the Ingestion: the cursor of how far they have been applied, and the application of a page of them. For the Catalogue Sync only.";

	String CLASSIFICATION = "Classification";
	String CLASSIFICATION_DESC = "The matching of each Retailer Product to at most one Archetype Product: the products waiting to be classified, and the recording of the attempts made outside this application, by an expert or one of us, from which each product's Classification is resolved. For the Catalogue Sync and operators only.";

	String TOKEN_EXCHANGE = "Token exchange";
	String TOKEN_EXCHANGE_DESC = "Exchange of a retailer's service-account token for one that acts as a user of that retailer, for a retailer calling this application on a user's behalf.";
}
