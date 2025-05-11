package eu.tealhelix.common.web.authentication.jwt;

import eu.tealhelix.common.v1.model.User;

/**
 * JWT generation service.
 */
public interface JwtGenerationService {
	TokenForImpersonationResult toTokenForImpersonation(User user);
}
