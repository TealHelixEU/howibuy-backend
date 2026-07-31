package eu.tealhelix.common.web.authentication.jwt;

import java.util.Date;

import com.nimbusds.jwt.JWTClaimsSet;

/**
 * What the validation of a token this application signed needs to read from its claims, together with the renewal that
 * carries a handoff session from one such token to the next.
 * <p>
 * All of it speaks in {@link JWTClaimsSet}, because all of it is about a token that already exists, so this interface
 * stays within the package that parses tokens. What the rest of the application mints from an identity is
 * {@link JwtGenerationService}.
 */
interface JwtClaimsService {
	/**
	 * Whether the token names its user by the id this application knows them by rather than the one the IDM does.
	 *
	 * @param jwtClaimsSet The claims of the token
	 * @return Whether the token stands for an impersonated user
	 */
	boolean isImpersonated(JWTClaimsSet jwtClaimsSet);

	/**
	 * Whether the token belongs to a session that a retailer handed over to the single-page application. Only such a
	 * token may be renewed; the one the retailer keeps for itself may not.
	 *
	 * @param jwtClaimsSet The claims of the token
	 * @return Whether the token belongs to a handoff session
	 */
	boolean isHandoff(JWTClaimsSet jwtClaimsSet);

	/**
	 * The point at which the handoff session the token belongs to ends, which no renewal may move.
	 *
	 * @param jwtClaimsSet The claims of the token
	 * @return The end of the session, or {@code null} if the token does not name one readably
	 */
	Date getHandoffExpiration(JWTClaimsSet jwtClaimsSet);

	/**
	 * Mint the next token of the handoff session that the given claims belong to. The new token names the same user and
	 * the same end of session, and expires after another handoff session time or when the session ends, whichever comes
	 * first.
	 *
	 * @param jwtClaimsSet The claims of the handoff token presented for renewal, as {@link TokenHelper} validated them
	 * @return The token
	 * @throws eu.tealhelix.common.types.authorization.NotAuthorizedException If the claims are not those of a handoff
	 *                                                                       token, or its session has ended
	 */
	IssuedToken renewHandoffToken(JWTClaimsSet jwtClaimsSet);
}
