package eu.tealhelix.common.web.authentication.jwt;

/**
 * A token this application signed, together with how long the holder may use it.
 *
 * @param accessToken      The serialized token
 * @param expiresInSeconds The seconds from the moment of issue until the token expires
 */
public record IssuedToken(String accessToken, int expiresInSeconds) {
}
