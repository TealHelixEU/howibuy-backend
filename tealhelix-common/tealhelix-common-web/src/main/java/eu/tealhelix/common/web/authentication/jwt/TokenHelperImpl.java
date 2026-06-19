package eu.tealhelix.common.web.authentication.jwt;

import java.text.ParseException;
import java.time.ZoneId;
import java.util.Date;
import java.util.Objects;
import java.util.Set;
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

	private static final Set<JWSAlgorithm> SUPPORTED_ALGORITHMS = Set.of(JWSAlgorithm.RS256, JWSAlgorithm.HS256);

	private final JWSVerifierMapper jwsVerifierMapper;
	private final DateTimeService dateTimeService;
	private final UserService userService;
	private final JwtGenerationService jwtGenerationService;
	private final TokenAuthenticationConfig tokenAuthenticationConfig;

	/**
	 * Injection constructor.
	 *
	 * @param jwsVerifierMapper         The map of JWT verifiers
	 * @param dateTimeService           The date service
	 * @param userService               The user service
	 * @param jwtGenerationService      The JWT generation service
	 * @param tokenAuthenticationConfig The configuration
	 */
	@Inject
	public TokenHelperImpl(
			JWSVerifierMapper jwsVerifierMapper,
			DateTimeService dateTimeService,
			UserService userService,
			JwtGenerationService jwtGenerationService,
			TokenAuthenticationConfig tokenAuthenticationConfig
	) {
		this.jwsVerifierMapper = jwsVerifierMapper;
		this.dateTimeService = dateTimeService;
		this.userService = userService;
		this.jwtGenerationService = jwtGenerationService;
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
		return parse(token)
				.flatMap(jwt -> {
					var alg = jwt.getHeader().getAlgorithm();
					if (alg != null && !SUPPORTED_ALGORITHMS.contains(alg)) {
						return Uni.createFrom().failure(new TokenHelperException("unsupported algorithm: " + jwt.getHeader().getAlgorithm().getName()));
					} else {
						return verify(jwt, token);
					}
				})
				.flatMap(jwt -> extractJWTClaimsSet(token, jwt))
				.flatMap(jwtClaimsSet -> {
					if (jwtClaimsSet.getExpirationTime().before(Date.from(dateTimeService.getNow().atZone(ZoneId.systemDefault()).toInstant()))) {
						return Uni.createFrom().failure(new TokenHelperException("JWT expired at " + jwtClaimsSet.getExpirationTime()));
					} else {
						return Uni.createFrom().item(jwtClaimsSet);
					}
				})
				.flatMap(jwtClaimsSet -> {
					var clientIdObj = jwtClaimsSet.getClaim(tokenAuthenticationConfig.getClientIdFieldInJwt());
					var userNameObj = jwtClaimsSet.getClaim(tokenAuthenticationConfig.getUsernameFieldInJwt());
					String userName = clientIdObj != null ? clientIdObj.toString() : (userNameObj != null ? userNameObj.toString() : null);
					boolean serviceFlag = clientIdObj != null;
					var userIdFromIdm = jwtClaimsSet.getClaim(tokenAuthenticationConfig.getUserIdFieldInJwt()).toString();
					var emailObj = jwtClaimsSet.getClaim(tokenAuthenticationConfig.getEmailFieldInJwt());
					var email = emailObj != null ? new EmailAddressImpl(emailObj.toString()) : null;

					if (serviceFlag) {
						var userImpl = new UserImpl(new UserIdImpl(userIdFromIdm), userName, email, false, true);
						return Uni.createFrom().item(userImpl);
					} else {
						if (jwtGenerationService.isImpersonated(jwtClaimsSet)) {
							return userService.requireUserWithId(new UserIdImpl(userIdFromIdm), userName, false)
									.flatMap(user -> {
										if (!Objects.equals(user.getEmail(), email)) {
											LOG.error("Emails in IDM and DB do not match, user {} (IDM) {} (DB)", userIdFromIdm, user.getId().asString());
											return Uni.createFrom().failure(new NotAuthorizedException("invalid data"));
										}
										if (!Objects.equals(user.getName(), userName)) {
											LOG.error("Names in IDM and DB do not match, user {} (IDM) {} (DB)", userIdFromIdm, user.getId().asString());
											return Uni.createFrom().failure(new NotAuthorizedException("invalid data"));
										}
										return Uni.createFrom().item(user);
									})
									.onFailure(NotFoundException.class)
									.transform(nfe -> {
										LOG.error("IDM user not found in DB, id {} (IDM)", userIdFromIdm);
										return new NotAuthorizedException("invalid data");
									});
						} else {
							return userService.requireUserFromValidIdmId(userIdFromIdm, userName, false)
									.flatMap(user -> {
										if (!Objects.equals(user.getEmail(), email)) {
											LOG.error("Emails in IDM and DB do not match, user {} (IDM) {} (DB)", userIdFromIdm, user.getId().asString());
											return Uni.createFrom().failure(new NotAuthorizedException("invalid data"));
										}
										if (!Objects.equals(user.getName(), userName)) {
											LOG.error("Names in IDM and DB do not match, user {} (IDM) {} (DB)", userIdFromIdm, user.getId().asString());
											return Uni.createFrom().failure(new NotAuthorizedException("invalid data"));
										}
										return Uni.createFrom().item(user);
									})
									.onFailure(NotFoundException.class)
									.transform(nfe -> {
										LOG.error("IDM user not found in DB, id {} (IDM)", userIdFromIdm);
										return new NotAuthorizedException("invalid data");
									});
						}
					}
				});
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

	@Override
	public User makeUnauthenticated() {
		return new UserImpl(null, null, null, false, false);
	}
}
