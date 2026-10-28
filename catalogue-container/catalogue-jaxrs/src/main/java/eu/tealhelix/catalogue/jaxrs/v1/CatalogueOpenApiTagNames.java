package eu.tealhelix.catalogue.jaxrs.v1;

public interface CatalogueOpenApiTagNames {
	String FEEDS = "Feeds";
	String FEEDS_DESC = "Receiving the Feeds of the Retailers. A Feed is stored once as it arrives and answered at once with its Ingestion Runs, one per Catalogue it is for, which are processed later. Retailers push Feeds for their own active Catalogues; a Feed is read by the Retailer that sent it and by the operators.";

	String INGESTION_RUNS = "Ingestion Runs";
	String INGESTION_RUNS_DESC = "How the Ingestion Runs stand: their status, timings and counts, and the records they rejected. For the Retailer of the run's Catalogue and for the operators.";

	String PRODUCT_CHANGES = "Product Changes";
	String PRODUCT_CHANGES_DESC = "The Retailer Products that changed after a value of the change sequence, each in its complete current state, read page by page. For the Catalogue Sync only.";
}
