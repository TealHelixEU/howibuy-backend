package eu.tealhelix.common.web.authentication.jwt;

import java.text.ParseException;
import java.util.Base64;
import java.util.Date;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.KeyLengthException;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.util.DateUtils;
import eu.tealhelix.common.services.generic.DateTimeService;
import eu.tealhelix.common.types.authorization.NotAuthorizedException;
import eu.tealhelix.common.v1.types.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of the {@link JwtGenerationService} and of the {@link JwtClaimsService}: what claims a token
 * carries is decided here, so reading them back belongs here too.
 *
 * @see <a href="https://connect2id.com/products/nimbus-jose-jwt/examples/jwt-with-hmac">example</a>
 */
@ApplicationScoped
public class JwtGenerationServiceImpl implements JwtGenerationService, JwtClaimsService {
	private static final Logger LOG = LoggerFactory.getLogger(JwtGenerationServiceImpl.class);

	private static final String CLAIM_IMPERSONATED = "impersonated";

	/**
	 * Marks a token as belonging to a session that a retailer handed over to the single-page application, which is the
	 * only kind of token this application renews.
	 */
	private static final String CLAIM_HANDOFF = "handoff";

	/**
	 * Names the end of the handoff session, in the seconds-since-the-epoch encoding that {@code exp} uses.
	 */
	private static final String CLAIM_HANDOFF_EXPIRATION = "handoff_exp";

	private final DateTimeService dateTimeService;
	private final TokenAuthenticationConfig tokenAuthenticationConfig;

	/**
	 * The signer, it is thread-safe as per specs. We are using the implementation, not the interface,
	 * {@code JWSSigner}, to emphasize this fact.
	 */
	private MACSigner signer;

	/**
	 * Constructor for injection.
	 *
	 * @param dateTimeService           The date and time service
	 * @param tokenAuthenticationConfig The token authentication configuration
	 */
	@Inject
	public JwtGenerationServiceImpl(DateTimeService dateTimeService, TokenAuthenticationConfig tokenAuthenticationConfig) {
		this.dateTimeService = dateTimeService;
		this.tokenAuthenticationConfig = tokenAuthenticationConfig;
	}

	@PostConstruct
	void init() {
		byte[] sharedSecret = Base64.getMimeDecoder().decode(tokenAuthenticationConfig.getJwtSecret());
		try {
			signer = new MACSigner(sharedSecret);
		} catch (KeyLengthException e) {
			throw new TokenGenerationException(e);
		}
	}

	@Override
	public IssuedToken toTokenForImpersonation(UserId userId) {
		var issuedAt = dateTimeService.currentTimeMillis();
		var expiration = issuedAt + millis(tokenAuthenticationConfig.getJwtSessionTimeInSeconds());
		return sign(claimsNaming(userId.asString(), expiration).build(), issuedAt, expiration);
	}

	@Override
	public IssuedToken toTokenForHandoff(UserId userId) {
		var issuedAt = dateTimeService.currentTimeMillis();
		var sessionEnd = issuedAt + millis(tokenAuthenticationConfig.getHandoffMaxSessionTimeInSeconds());
		return handoffToken(userId.asString(), issuedAt, sessionEnd);
	}

	@Override
	public IssuedToken renewHandoffToken(JWTClaimsSet jwtClaimsSet) {
		var sessionEnd = requireRenewableSession(jwtClaimsSet);
		var userId = jwtClaimsSet.getClaim(tokenAuthenticationConfig.getUserIdFieldInJwt());
		return handoffToken(userId.toString(), dateTimeService.currentTimeMillis(), sessionEnd.getTime());
	}

	/**
	 * A handoff token expires after the handoff session time or when the session it belongs to ends, whichever comes
	 * first: the end of the session is what keeps a chain of renewals finite, so no token of the chain may outlive it.
	 */
	private IssuedToken handoffToken(String userId, long issuedAt, long sessionEnd) {
		var handoffTime = issuedAt + millis(tokenAuthenticationConfig.getHandoffSessionTimeInSeconds());
		var expiration = Math.min(handoffTime, sessionEnd);
		var claims = claimsNaming(userId, expiration)
				.claim(CLAIM_HANDOFF, true)
				.claim(CLAIM_HANDOFF_EXPIRATION, DateUtils.toSecondsSinceEpoch(new Date(sessionEnd)))
				.build();
		return sign(claims, issuedAt, expiration);
	}

	/**
	 * The token the retailer keeps for itself is not renewable; only a token of a handed-over session is, and only while
	 * that session lasts.
	 *
	 * @return The end of the session the token belongs to
	 */
	private Date requireRenewableSession(JWTClaimsSet jwtClaimsSet) {
		var sessionEnd = getHandoffExpiration(jwtClaimsSet);
		if (!isHandoff(jwtClaimsSet) || sessionEnd == null) {
			LOG.warn("Refused to renew a token that does not belong to a handoff session");
			throw new NotAuthorizedException("not a handoff token");
		}
		if (!sessionEnd.after(new Date(dateTimeService.currentTimeMillis()))) {
			LOG.info("Refused to renew a handoff session that ended at {}", sessionEnd);
			throw new NotAuthorizedException("handoff session has ended");
		}
		return sessionEnd;
	}

	/**
	 * The token names the user by id and says nothing else about them: the id is all that the validation of an
	 * impersonation token reads, and the token is held by a retailer, so anything more would be telling the retailer
	 * something it did not ask for.
	 */
	private JWTClaimsSet.Builder claimsNaming(String userId, long expiration) {
		return new JWTClaimsSet.Builder()
				.claim(tokenAuthenticationConfig.getUserIdFieldInJwt(), userId)
				.claim(CLAIM_IMPERSONATED, true)
				.expirationTime(new Date(expiration));
	}

	private IssuedToken sign(JWTClaimsSet claims, long issuedAt, long expiration) {
		var header = new JWSHeader.Builder(JWSAlgorithm.HS256)
				.keyID(tokenAuthenticationConfig.getInternalKeyId())
				.build();
		var signedJWT = new SignedJWT(header, claims);
		try {
			signedJWT.sign(signer);
		} catch (JOSEException e) {
			throw new TokenGenerationException(e);
		}
		return new IssuedToken(signedJWT.serialize(), (int) ((expiration - issuedAt) / 1000));
	}

	private static long millis(int seconds) {
		return seconds * 1000L;
	}

	@Override
	public boolean isImpersonated(JWTClaimsSet jwtClaimsSet) {
		return Boolean.TRUE.equals(jwtClaimsSet.getClaim(CLAIM_IMPERSONATED));
	}

	@Override
	public boolean isHandoff(JWTClaimsSet jwtClaimsSet) {
		return Boolean.TRUE.equals(jwtClaimsSet.getClaim(CLAIM_HANDOFF));
	}

	@Override
	public Date getHandoffExpiration(JWTClaimsSet jwtClaimsSet) {
		try {
			return jwtClaimsSet.getDateClaim(CLAIM_HANDOFF_EXPIRATION);
		} catch (ParseException e) {
			LOG.warn("A token names the end of its handoff session in a form that cannot be read: {}",
					jwtClaimsSet.getClaim(CLAIM_HANDOFF_EXPIRATION));
			return null;
		}
	}
}
