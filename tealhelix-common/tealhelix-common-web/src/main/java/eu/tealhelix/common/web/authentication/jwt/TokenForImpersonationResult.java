package eu.tealhelix.common.web.authentication.jwt;

public record TokenForImpersonationResult(String accessToken, int expiresInSeconds) {
}
