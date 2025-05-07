package eu.tealhelix.common.web.authentication.jwt;

import java.text.ParseException;
import java.time.ZoneId;
import java.util.Date;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import eu.tealhelix.common.services.generic.DateTimeService;
import eu.tealhelix.common.types.impl.EmailImpl;
import eu.tealhelix.common.v1.model.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TokenHelperImpl implements TokenHelper {

	private final JWSVerifierMapper jwsVerifierMapper;
	private final DateTimeService dateTimeService;
	private final TokenAuthenticationConfig tokenAuthenticationConfig;

	/**
	 * Injection constructor.
	 *
	 * @param jwsVerifierMapper         The map of JWT verifiers
	 * @param dateTimeService           The date service
	 * @param tokenAuthenticationConfig The configuration
	 */
	@Inject
	public TokenHelperImpl(JWSVerifierMapper jwsVerifierMapper, DateTimeService dateTimeService, TokenAuthenticationConfig tokenAuthenticationConfig) {
		this.jwsVerifierMapper = jwsVerifierMapper;
		this.dateTimeService = dateTimeService;
		this.tokenAuthenticationConfig = tokenAuthenticationConfig;
	}


	/**
	 * Process the token to create a {@link User}.
	 *
	 * @param token The token, cannot be {@code null}
	 * @return The user
	 */
	@Override
	public User processToken(String token) {
		try {
			SignedJWT jwt = parse(token);

			if (!"RS256".equals(jwt.getHeader().getAlgorithm().getName())) {
				throw new TokenHelperException("unsupported algorithm: " + jwt.getHeader().getAlgorithm().getName());
			}

			String kid = jwt.getHeader().getKeyID();
			JWSVerifier verifier = jwsVerifierMapper.get(kid);
			if (verifier == null) {
				throw new TokenHelperException("unknown kid: " + kid);
			}

			if (!jwt.verify(verifier)) {
				throw new TokenHelperException("failed to verify JWT: " + token);
			}

			JWTClaimsSet jwtClaimsSet = extractJWTClaimsSet(token, jwt);
			if (jwtClaimsSet.getExpirationTime().before(Date.from(dateTimeService.getNow().atZone(ZoneId.systemDefault()).toInstant()))) {
				throw new TokenHelperException("JWT expired at " + jwtClaimsSet.getExpirationTime());
			}

			var userName = jwtClaimsSet.getClaim(tokenAuthenticationConfig.getUsernameFieldInJwt()).toString();
			var userIdStr = jwtClaimsSet.getClaim(tokenAuthenticationConfig.getUserIdFieldInJwt()).toString();
			var userId = new UserIdImpl(userIdStr);
			var emailStr = jwtClaimsSet.getClaim(tokenAuthenticationConfig.getEmailFieldInJwt()).toString();
			var email = new EmailImpl(emailStr);
			return new UserImpl(userId, userName, email, false);
		} catch (JOSEException e) {
			throw new TokenHelperException("failed to process token: " + token, e);
		}
	}

	private SignedJWT parse(String token) {
		try {
			return SignedJWT.parse(token);
		} catch (ParseException e) {
			throw new TokenHelperException("error parsing token " + token, e);
		}
	}

	private JWTClaimsSet extractJWTClaimsSet(String token, SignedJWT jwt) {
		try {
			return jwt.getJWTClaimsSet();
		} catch (ParseException e) {
			throw new TokenHelperException("failed to extract claims:" + token, e);
		}
	}

	@Override
	public User makeUnauthenticated() {
		return new UserImpl(null, null, null, false);
	}
}
