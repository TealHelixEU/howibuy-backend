package eu.tealhelix.betterme;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

@ApplicationPath(JaxRsAppBetterMeV1.APPLICATION_PATH)
public class JaxRsAppBetterMeV1 extends Application {
	/**
	 * The JAX-RS application path.
	 */
	public static final String APPLICATION_PATH = "/api/betterme/v1";
}
