package eu.tealhelix.howibuy;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

@ApplicationPath(JaxRsAppHowiBuyV1.APPLICATION_PATH)
public class JaxRsAppHowiBuyV1 extends Application {
	/**
	 * The JAX-RS application path.
	 */
	public static final String APPLICATION_PATH = "/api/howibuy/v1";
}
