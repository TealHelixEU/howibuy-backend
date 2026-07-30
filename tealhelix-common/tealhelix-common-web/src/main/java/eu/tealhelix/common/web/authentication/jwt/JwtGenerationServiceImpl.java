package eu.tealhelix.common.web.authentication.jwt;

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
import eu.tealhelix.common.services.generic.DateTimeService;
import eu.tealhelix.common.v1.model.User;

/**
 * Default implementation of the {@link JwtGenerationService}.
 *
 * @see <a href="https://connect2id.com/products/nimbus-jose-jwt/examples/jwt-with-hmac">example</a>
 */
@ApplicationScoped
public class JwtGenerationServiceImpl implements JwtGenerationService {
	private static final String CLAIM_IMPERSONATED = "impersonated";

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
	public TokenForImpersonationResult toTokenForImpersonation(User user) {
		var expiresInSeconds = tokenAuthenticationConfig.getJwtSessionTimeInSeconds();
		var expirationTime = dateTimeService.currentTimeMillis() + expiresInSeconds * 1000L;
		SignedJWT signedJWT = makeSignedJWTForImpersonation(user.getId().asString(), new Date(expirationTime));
		return new TokenForImpersonationResult(signedJWT.serialize(), expiresInSeconds);
	}

	/**
	 * The token names the user by id and says nothing else about them: the id is all that the validation of an
	 * impersonation token reads, and the token is held by a retailer, so anything more would be telling the retailer
	 * something it did not ask for.
	 */
	private SignedJWT makeSignedJWTForImpersonation(String userId, Date expirationTime) {
		JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
				.claim(tokenAuthenticationConfig.getUserIdFieldInJwt(), userId)
				.claim(CLAIM_IMPERSONATED, true)
				.expirationTime(expirationTime)
				.build();
		var header = new JWSHeader.Builder(JWSAlgorithm.HS256)
				.keyID(tokenAuthenticationConfig.getInternalKeyId())
				.build();
		SignedJWT signedJWT = new SignedJWT(header, claimsSet);
		try {
			signedJWT.sign(signer);
		} catch (JOSEException e) {
			throw new TokenGenerationException(e);
		}
		return signedJWT;
	}

	@Override
	public boolean isImpersonated(JWTClaimsSet jwtClaimsSet) {
		return Boolean.TRUE.equals(jwtClaimsSet.getClaim(CLAIM_IMPERSONATED));
	}
}
