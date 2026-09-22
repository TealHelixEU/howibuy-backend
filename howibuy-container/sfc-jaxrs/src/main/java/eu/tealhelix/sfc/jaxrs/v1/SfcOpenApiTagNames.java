package eu.tealhelix.sfc.jaxrs.v1;

public interface SfcOpenApiTagNames {
	String SFC_ATTEMPTS = "Sustainable Food Compass attempts";
	String SFC_ATTEMPTS_DESC = "Writes to the authenticated end-user's compass attempt: answering a question, starting a fresh attempt, and completing the one in progress — which freezes it as the settled record of what the user cares about; users can revisit their answers only after some time has passed (the stability window).";

	String SFC = "Sustainable Food Compass";
	String SFC_DESC = "Reads of the compass for the authenticated end-user: the categories and questions it is made of, each paired with its answer, and the navigation that walks the user through a category.";
}
