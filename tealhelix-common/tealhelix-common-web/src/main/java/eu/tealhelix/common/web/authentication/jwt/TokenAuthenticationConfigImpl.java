package eu.tealhelix.common.web.authentication.jwt;

import java.net.URL;
import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Implementation of {@link TokenAuthenticationConfig} using the facilities of Microprofile Config.
 */
@ApplicationScoped
public class TokenAuthenticationConfigImpl implements TokenAuthenticationConfig {

	public static final String JWK_URL_KEY = "config.jwk.url";
	public static final String USERNAME_FIELD_IN_JWT_KEY = "config.jwt.map.userName";
	public static final String USERID_FIELD_IN_JWT_KEY = "config.jwt.map.userId";
	public static final String CLIENT_ID_FIELD_IN_JWT_KEY = "config.jwt.map.clientId";
	public static final String EMAIL_FIELD_IN_JWT_KEY = "config.jwt.map.email";
	public static final String JWT_SECRET_KEY = "config.jwt.secret";
	public static final String JWT_SESSION_TIME_KEY = "config.jwt.sessionTimeInSeconds";

	@ConfigProperty(name = USERNAME_FIELD_IN_JWT_KEY)
	String usernameFieldInJwt;

	@ConfigProperty(name = USERID_FIELD_IN_JWT_KEY)
	String userIdFieldInJwt;

	@ConfigProperty(name = CLIENT_ID_FIELD_IN_JWT_KEY)
	String clientIdFieldInJwt;

	@ConfigProperty(name = EMAIL_FIELD_IN_JWT_KEY)
	String emailFieldInJwt;

	@ConfigProperty(name = JWT_SECRET_KEY)
	String jwtSecret;

	@ConfigProperty(name = JWT_SESSION_TIME_KEY)
	Integer jwtSessionTimeInSeconds;

	@ConfigProperty(name = JWK_URL_KEY)
	URL jwkUrl;

	@Override
	public URL getJwkUrl() {
		return jwkUrl;
	}

	@Override
	public String getUsernameFieldInJwt() {
		return usernameFieldInJwt;
	}

	@Override
	public String getUserIdFieldInJwt() {
		return userIdFieldInJwt;
	}

	@Override
	public String getClientIdFieldInJwt() {
		return clientIdFieldInJwt;
	}

	@Override
	public String getEmailFieldInJwt() {
		return emailFieldInJwt;
	}

	@Override
	public String getJwtSecret() {
		return jwtSecret;
	}

	@Override
	public Integer getJwtSessionTimeInSeconds() {
		return jwtSessionTimeInSeconds;
	}
}
