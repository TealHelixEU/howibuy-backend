package eu.tealhelix.common.web.authentication.jwt;

import com.nimbusds.jwt.JWTClaimsSet;
import eu.tealhelix.common.v1.model.User;

/**
 * JWT generation service.
 */
public interface JwtGenerationService {
	TokenForImpersonationResult toTokenForImpersonation(User user);

	boolean isImpersonated(JWTClaimsSet jwtClaimsSet);
}
