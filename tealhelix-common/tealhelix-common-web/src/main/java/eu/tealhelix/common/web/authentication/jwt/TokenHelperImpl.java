package eu.tealhelix.common.web.authentication.jwt;

import static eu.tealhelix.common.utils.UniComprehensions.forc;

import java.text.ParseException;
import java.time.ZoneId;
import java.util.Date;
import java.util.Objects;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import eu.tealhelix.common.services.generic.DateTimeService;
import eu.tealhelix.common.services.generic.UserService;
import eu.tealhelix.common.types.authorization.NotAuthorizedException;
import eu.tealhelix.common.types.entity.NotFoundException;
import eu.tealhelix.common.types.impl.EmailAddressImpl;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import io.smallrye.mutiny.Uni;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class TokenHelperImpl implements TokenHelper {
	private static final Logger LOG = LoggerFactory.getLogger(TokenHelperImpl.class);

	/**
	 * The algorithm the IDM signs with; its keys are RSA, published through the JWK set.
	 */
	private static final JWSAlgorithm IDM_ALGORITHM = JWSAlgorithm.RS256;

	/**
	 * The algorithm this application signs with; the key is the shared secret behind the internal key id.
	 */
	private static final JWSAlgorithm INTERNAL_ALGORITHM = JWSAlgorithm.HS256;

	/**
	 * The claim naming the client the IDM issued the token to.
	 */
	private static final String AUTHORIZED_PARTY_CLAIM = "azp";

	/**
	 * The claim naming the kind of token the IDM issued; access tokens, ID tokens and refresh tokens differ here.
	 */
	private static final String TOKEN_TYPE_CLAIM = "typ";

	/**
	 * The value of {@link #TOKEN_TYPE_CLAIM} that marks an access token.
	 */
	private static final String ACCESS_TOKEN_TYPE = "Bearer";

	private final JWSVerifierMapper jwsVerifierMapper;
	private final DateTimeService dateTimeService;
	private final UserService userService;
	private final JwtClaimsService jwtClaimsService;
	private final TokenAuthenticationConfig tokenAuthenticationConfig;

	/**
	 * Injection constructor.
	 *
	 * @param jwsVerifierMapper         The map of JWT verifiers
	 * @param dateTimeService           The date service
	 * @param userService               The user service
	 * @param jwtClaimsService          The reader of the claims of tokens this application signed
	 * @param tokenAuthenticationConfig The configuration
	 */
	@Inject
	public TokenHelperImpl(
			JWSVerifierMapper jwsVerifierMapper,
			DateTimeService dateTimeService,
			UserService userService,
			JwtClaimsService jwtClaimsService,
			TokenAuthenticationConfig tokenAuthenticationConfig
	) {
		this.jwsVerifierMapper = jwsVerifierMapper;
		this.dateTimeService = dateTimeService;
		this.userService = userService;
		this.jwtClaimsService = jwtClaimsService;
		this.tokenAuthenticationConfig = tokenAuthenticationConfig;
	}

	/**
	 * Process the token to create a {@link User}.
	 *
	 * @param token The token, cannot be {@code null}
	 * @return The user, asynchronously
	 */
	@Override
	public Uni<User> processToken(String token) {
		return validatedClaims(token)
				.flatMap(jwtClaimsSet -> {
					var clientIdObj = jwtClaimsSet.getClaim(tokenAuthenticationConfig.getClientIdFieldInJwt());
					var userNameObj = jwtClaimsSet.getClaim(tokenAuthenticationConfig.getUsernameFieldInJwt());
					String userName = clientIdObj != null ? clientIdObj.toString() : (userNameObj != null ? userNameObj.toString() : null);
					boolean serviceFlag = isServiceToken(jwtClaimsSet);
					var userIdFromIdm = jwtClaimsSet.getClaim(tokenAuthenticationConfig.getUserIdFieldInJwt()).toString();
					var emailObj = jwtClaimsSet.getClaim(tokenAuthenticationConfig.getEmailFieldInJwt());
					var email = emailObj != null ? new EmailAddressImpl(emailObj.toString()) : null;

					if (serviceFlag) {
						var userImpl = new UserImpl(new UserIdImpl(userIdFromIdm), userName, email, false, true);
						return Uni.createFrom().item(userImpl);
					} else {
						if (jwtClaimsService.isImpersonated(jwtClaimsSet)) {
							return userService.requireUserWithId(new UserIdImpl(userIdFromIdm), userName, false)
									.onFailure(NotFoundException.class)
									.transform(nfe -> logAndMapToNotAuthorizedException(nfe, userIdFromIdm));
						} else {
							return userService.requireUserFromValidIdmId(userIdFromIdm, userName, false)
									.onFailure(NotFoundException.class)
									.transform(nfe -> logAndMapToNotAuthorizedException(nfe, userIdFromIdm));
						}
					}
				});
	}

	@Override
	public Uni<IssuedToken> renewHandoffToken(String token) {
		return validatedClaims(token).map(jwtClaimsService::renewHandoffToken);
	}

	/**
	 * The claims of a token this application is prepared to act on: parsed, signed by a key of its family with that
	 * family's algorithm, and carrying the claims that family must carry. Renewal asks a token for exactly this before it
	 * will extend it, so a token that could not authenticate a request cannot be renewed either.
	 */
	private Uni<JWTClaimsSet> validatedClaims(String token) {
		return forc(
				parse(token),
				this::requireExpectedAlgorithm,
				jwt -> verify(jwt, token),
				jwt -> extractJWTClaimsSet(token, jwt),
				this::validateClaims
		);
	}

	/**
	 * Tells the two token families apart by the key id in the JWS header: this application signs with the configured
	 * internal key id, the IDM signs with the keys it publishes in its JWK set. Which family a token belongs to decides
	 * both the algorithm it must carry and the claims it must satisfy.
	 *
	 * @param jwt The token
	 * @return Whether this application issued the token itself
	 */
	private boolean isInternalToken(SignedJWT jwt) {
		var internalKeyId = tokenAuthenticationConfig.getInternalKeyId();
		return internalKeyId != null && internalKeyId.equals(jwt.getHeader().getKeyID());
	}

	/**
	 * Binds the signing algorithm to the token family, so that a token cannot be presented under the algorithm of the
	 * other family.
	 */
	private Uni<SignedJWT> requireExpectedAlgorithm(SignedJWT jwt) {
		var expected = isInternalToken(jwt) ? INTERNAL_ALGORITHM : IDM_ALGORITHM;
		var actual = jwt.getHeader().getAlgorithm();
		if (!expected.equals(actual)) {
			return Uni.createFrom().failure(new TokenHelperException(
					"unexpected algorithm, expected: " + expected.getName() + " actual: " + actual));
		} else {
			return Uni.createFrom().item(jwt);
		}
	}

	/**
	 * Tells a token the IDM issued to a client acting on its own behalf from one it issued for a user: the IDM names the
	 * client in the token of the former, and leaves that claim out of the token of the latter.
	 *
	 * @param jwtClaimsSet The claims of the token
	 * @return Whether the token represents a client rather than a user
	 */
	private boolean isServiceToken(JWTClaimsSet jwtClaimsSet) {
		return jwtClaimsSet.getClaim(tokenAuthenticationConfig.getClientIdFieldInJwt()) != null;
	}

	/**
	 * Applies the claim validation of the token's family. Both families must name a user and must not have expired;
	 * beyond that, a token from the IDM must come from the expected realm and must be an access token, while a token
	 * this application issued must carry the impersonation marker that both of its uses put there, and, where it belongs
	 * to a session handed over to the single-page application, must name an end of that session that has not passed.
	 * <p>
	 * A token the IDM issued for a user must also come from a client that users may reach this application through,
	 * because such a token carries everything a user is allowed to do. A token the IDM issued to a client acting on its
	 * own behalf is not restricted here: it grants nothing until the application recognises the client in its own
	 * records, which is a question for the services the client goes on to call.
	 */
	private Uni<JWTClaimsSet> validateClaims(SignedJWT jwt, JWTClaimsSet jwtClaimsSet) {
		try {
			requireUnexpired(jwtClaimsSet);
			requireUserId(jwtClaimsSet);
			if (isInternalToken(jwt)) {
				requireImpersonationMarker(jwtClaimsSet);
				if (jwtClaimsService.isHandoff(jwtClaimsSet)) {
					requireUnendedHandoffSession(jwtClaimsSet);
				}
			} else {
				requireExpectedIssuer(jwtClaimsSet);
				requireAccessTokenType(jwtClaimsSet);
				if (!isServiceToken(jwtClaimsSet)) {
					requireAllowedUserClient(jwtClaimsSet);
				}
			}
			return Uni.createFrom().item(jwtClaimsSet);
		} catch (TokenHelperException e) {
			return Uni.createFrom().failure(e);
		}
	}

	private void requireUnexpired(JWTClaimsSet jwtClaimsSet) {
		var expirationTime = jwtClaimsSet.getExpirationTime();
		if (expirationTime == null) {
			throw new TokenHelperException("JWT carries no expiration time");
		}
		if (expirationTime.before(now())) {
			throw new TokenHelperException("JWT expired at " + expirationTime);
		}
	}

	/**
	 * A handoff token names the end of the session it belongs to, which the renewal of such a token may not move. Every
	 * token of the session expires at or before that point, so this only ever refuses a token whose expiration time and
	 * end of session contradict each other; the end of the session is what bounds a chain of renewals, and checking it
	 * here does not leave that to the arithmetic of a single mint.
	 */
	private void requireUnendedHandoffSession(JWTClaimsSet jwtClaimsSet) {
		var sessionEnd = jwtClaimsService.getHandoffExpiration(jwtClaimsSet);
		if (sessionEnd == null) {
			throw new TokenHelperException("handoff JWT does not name the end of its session");
		}
		if (sessionEnd.before(now())) {
			throw new TokenHelperException("handoff JWT belongs to a session that ended at " + sessionEnd);
		}
	}

	private Date now() {
		return Date.from(dateTimeService.getNow().atZone(ZoneId.systemDefault()).toInstant());
	}

	private void requireUserId(JWTClaimsSet jwtClaimsSet) {
		var userIdField = tokenAuthenticationConfig.getUserIdFieldInJwt();
		if (jwtClaimsSet.getClaim(userIdField) == null) {
			throw new TokenHelperException("JWT carries no user id, expected in claim: " + userIdField);
		}
	}

	private void requireImpersonationMarker(JWTClaimsSet jwtClaimsSet) {
		if (!jwtClaimsService.isImpersonated(jwtClaimsSet)) {
			throw new TokenHelperException("JWT signed with the internal key is not marked as impersonated");
		}
	}

	private void requireExpectedIssuer(JWTClaimsSet jwtClaimsSet) {
		var expectedIssuer = tokenAuthenticationConfig.getExpectedIssuer();
		if (!Objects.equals(expectedIssuer, jwtClaimsSet.getIssuer())) {
			throw new TokenHelperException(
					"JWT from an unexpected issuer, expected: " + expectedIssuer + " actual: " + jwtClaimsSet.getIssuer());
		}
	}

	private void requireAllowedUserClient(JWTClaimsSet jwtClaimsSet) {
		var authorizedParty = jwtClaimsSet.getClaim(AUTHORIZED_PARTY_CLAIM);
		var allowedUserClients = tokenAuthenticationConfig.getAllowedUserClients();
		if (authorizedParty == null || allowedUserClients == null || !allowedUserClients.contains(authorizedParty.toString())) {
			throw new TokenHelperException("JWT issued for a user to a client this application does not accept, client: " + authorizedParty);
		}
	}

	private void requireAccessTokenType(JWTClaimsSet jwtClaimsSet) {
		var tokenType = jwtClaimsSet.getClaim(TOKEN_TYPE_CLAIM);
		if (tokenType == null || !ACCESS_TOKEN_TYPE.equals(tokenType.toString())) {
			throw new TokenHelperException("JWT is not an access token, type: " + tokenType);
		}
	}

	private Uni<SignedJWT> parse(String token) {
		try {
			return Uni.createFrom().item(SignedJWT.parse(token));
		} catch (ParseException e) {
			return Uni.createFrom().failure(new TokenHelperException("error parsing token " + token, e));
		}
	}

	private Uni<SignedJWT> verify(SignedJWT jwt, String token) {
		try {
			String kid = jwt.getHeader().getKeyID();
			JWSVerifier verifier = jwsVerifierMapper.get(kid);
			if (verifier == null) {
				return Uni.createFrom().failure(new TokenHelperException("unknown kid: " + kid));
			}

			if (!jwt.verify(verifier)) {
				return Uni.createFrom().failure(new TokenHelperException("failed to verify JWT: " + token));
			}

			return Uni.createFrom().item(jwt);
		} catch (JOSEException e) {
			return Uni.createFrom().failure(new TokenHelperException("failed to process token: " + token, e));
		}
	}

	private Uni<JWTClaimsSet> extractJWTClaimsSet(String token, SignedJWT jwt) {
		try {
			return Uni.createFrom().item(jwt.getJWTClaimsSet());
		} catch (ParseException e) {
			return Uni.createFrom().failure(new TokenHelperException("failed to extract claims:" + token, e));
		}
	}

	private NotAuthorizedException logAndMapToNotAuthorizedException(NotFoundException nfe, String userIdFromIdm) {
		LOG.error("IDM user not found in DB, id {} (IDM)", userIdFromIdm);
		return new NotAuthorizedException("invalid data");
	}

	@Override
	public User makeUnauthenticated() {
		return new UserImpl(null, null, null, false, false);
	}
}
