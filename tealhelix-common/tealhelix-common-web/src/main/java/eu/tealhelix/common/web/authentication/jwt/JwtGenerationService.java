package eu.tealhelix.common.web.authentication.jwt;

import eu.tealhelix.common.v1.types.UserId;

/**
 * JWT generation service. It mints a token for a user this application already knows, which is all a caller outside
 * this package ever needs; reading the claims of a token that exists is the concern of the package itself.
 */
public interface JwtGenerationService {
	/**
	 * Mint the token a retailer uses to act for one of its users, as the token exchange hands it out. Such a token lasts
	 * a whole session time and cannot be renewed.
	 *
	 * @param userId The user the token stands for
	 * @return The token
	 */
	IssuedToken toTokenForImpersonation(UserId userId);

	/**
	 * Mint the first token of a session a retailer hands over to the single-page application. Such a token is
	 * short-lived and renewable, but the session it opens ends at a point that renewal cannot move.
	 *
	 * @param userId The user the token stands for
	 * @return The token
	 */
	IssuedToken toTokenForHandoff(UserId userId);
}
