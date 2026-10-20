package eu.tealhelix.catalogue.jaxrs.v1;

public interface CatalogueOpenApiTagNames {
	String FEEDS = "Feeds";
	String FEEDS_DESC = "Receiving the Feeds of the Retailers. A Feed is stored as it arrives and answered at once with its Ingestion Run, which is processed later. For Retailers only, each for its own active Catalogues.";

	String INGESTION_RUNS = "Ingestion Runs";
	String INGESTION_RUNS_DESC = "How the Ingestion Runs stand: their status, timings and counts, and the records they rejected. For the Retailer of the run's Catalogue and for the operators.";
}
