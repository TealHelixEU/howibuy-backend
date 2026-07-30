package eu.tealhelix.common.web.authentication.jwt;

import java.net.URL;
import java.util.List;

/**
 * Configuration for token authentication.
 */
public interface TokenAuthenticationConfig {

	/**
	 * The default JWK cache maximum TTL, see {@link #getJwkCacheMaxTtl()}.
	 */
	long MAX_CACHE_TTL = 120 * 60 * 1000; // 2 hours

	/**
	 * The default JWK cache (minimum) TTL, see {@link #getJwkCacheTtl()}.
	 */
	long MIN_CACHE_TTL = 5 * 60 * 1000; // 5 minutes

	/**
	 * Unused in the current configuration.
	 */
	URL getJwkUrl();

	/**
	 * The issuer that a token from the IDM must declare in its {@code iss} claim. This refers to the same IDM realm
	 * that serves {@link #getJwkUrl()}; the two are configured separately and must be kept in agreement.
	 *
	 * @return The expected issuer of IDM tokens
	 */
	String getExpectedIssuer();

	/**
	 * The IDM clients through which an end user may reach this application, matched against the {@code azp} claim. A
	 * token the IDM issued for a user to any other client of the same realm is rejected, so that registering a client in
	 * the realm does not by itself let that client act as one of this application's users. Tokens the IDM issued to a
	 * client acting on its own behalf are not matched against this list; what such a client may do is decided by the
	 * application's own records of it.
	 *
	 * @return The client ids through which users may reach this application
	 */
	List<String> getAllowedUserClients();

	/**
	 * The field of the JWT that maps to the user name.
	 *
	 * @return The field of the JWT that maps to the user name.
	 */
	String getUsernameFieldInJwt();

	/**
	 * The field of the JWT that maps to the user id.
	 *
	 * @return The field of the JWT that maps to the user id
	 */
	String getUserIdFieldInJwt();

	/**
	 * The field of the JWT that contains the client id in the case of service accounts.
	 *
	 * @return The field of the JWT that contains the client id in the case of service accounts
	 */
	String getClientIdFieldInJwt();

	/**
	 * The field of the JWT that maps to the user's email.
	 *
	 * @return The field of the JWT that maps to the user's email
	 */
	String getEmailFieldInJwt();

	/**
	 * The JWT secret.
	 *
	 * @return The JWT secret
	 */
	String getJwtSecret();

	/**
	 * The JWK key id of the key used to sign the tokens issued by this application (see token exchange).
	 *
	 * @return The internal key id
	 */
	String getInternalKeyId();

	/**
	 * The JWT session validity time in seconds.
	 *
	 * @return The JWT session validity time in seconds
	 */
	Integer getJwtSessionTimeInSeconds();

	/**
	 * The maximum time to live for the cached JWK; if last retrieval is before this time,
	 * the cache is discarded, and the JWK fetched anew. This minimizes the window within
	 * which compromised keys are valid for an attack.
	 */
	default long getJwkCacheMaxTtl() {
		return MAX_CACHE_TTL;
	}

	/**
	 * The minimum time before requesting a new JWK (e.g., because a new key id was sent by
	 * a client). This stops denial-of-service attacks where the attacker sends non-existing
	 * kids, and we are forced to fetch the JWK continuously. It also leaves a window of time
	 * when new, legitimate kids will not be acknowledged by the system.
	 */
	default long getJwkCacheTtl() {
		return MIN_CACHE_TTL;
	}
}
