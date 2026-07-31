package eu.tealhelix.common.web.authentication.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.text.ParseException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import eu.tealhelix.common.services.generic.DateTimeService;
import eu.tealhelix.common.services.generic.UserService;
import eu.tealhelix.common.types.authorization.NotAuthorizedException;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.UserId;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests the tokens that {@link JwtGenerationServiceImpl} mints. Both families stand for a user towards this application
 * while somebody else holds them, so what they do and do not say about that user matters. The token the retailer keeps
 * is valid for one session time and cannot be renewed; the token handed over to the single-page application is
 * short-lived, renewable, and bounded by an end of session that no renewal may move. Each family is also minted and
 * then validated once, because the two sides have to agree on where the user and that end of session are named.
 */
@ExtendWith(MockitoExtension.class)
public class JwtGenerationServiceImplTest {
	private static final long ASYNC_WAIT_SECONDS = 30;

	private static final String INTERNAL_KID = "howibuy:1";
	private static final int SESSION_TIME_SECONDS = 3600;
	private static final int HANDOFF_SESSION_TIME_SECONDS = 900;
	private static final int HANDOFF_MAX_SESSION_TIME_SECONDS = 8 * 60 * 60;

	private static final String USERID_FIELD = "sub";
	private static final String USERNAME_FIELD = "preferred_username";
	private static final String CLIENT_ID_FIELD = "client_id";
	private static final String EMAIL_FIELD = "email";

	private static final UserId USER_ID = new UserIdImpl("518cae6a-f2b2-4454-b74d-f2404feab2f5");
	private static final String USER_NAME = "bob@krusty-krab.com";

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
	private static final long NOW_MILLIS = NOW.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

	/** A 512-bit secret, the shape {@code config.jwt.secret} has; only ever used by this test. */
	private static final String JWT_SECRET =
			"nRvyYC4soFxBdZ-F-5Nnzz5USXstR1YylsTd-mA0aKtI9HUlriGrtkf-TiuDapkLiUCogO3JOK7kwZisrHp6wA";

	@Mock
	private DateTimeService dateTimeService;

	@Mock
	private TokenAuthenticationConfig tokenAuthenticationConfig;

	@Mock
	private JWSVerifierMapper jwsVerifierMapper;

	@Mock
	private UserService userService;

	private JwtGenerationServiceImpl sut;

	@BeforeEach
	void setUp() {
		lenient().when(tokenAuthenticationConfig.getUserIdFieldInJwt()).thenReturn(USERID_FIELD);
		lenient().when(tokenAuthenticationConfig.getUsernameFieldInJwt()).thenReturn(USERNAME_FIELD);
		lenient().when(tokenAuthenticationConfig.getClientIdFieldInJwt()).thenReturn(CLIENT_ID_FIELD);
		lenient().when(tokenAuthenticationConfig.getEmailFieldInJwt()).thenReturn(EMAIL_FIELD);
		lenient().when(tokenAuthenticationConfig.getInternalKeyId()).thenReturn(INTERNAL_KID);
		lenient().when(tokenAuthenticationConfig.getJwtSecret()).thenReturn(JWT_SECRET);
		lenient().when(tokenAuthenticationConfig.getJwtSessionTimeInSeconds()).thenReturn(SESSION_TIME_SECONDS);
		lenient().when(tokenAuthenticationConfig.getHandoffSessionTimeInSeconds()).thenReturn(HANDOFF_SESSION_TIME_SECONDS);
		lenient().when(tokenAuthenticationConfig.getHandoffMaxSessionTimeInSeconds()).thenReturn(HANDOFF_MAX_SESSION_TIME_SECONDS);

		lenient().when(dateTimeService.currentTimeMillis()).thenReturn(NOW_MILLIS);
		lenient().when(dateTimeService.getNow()).thenReturn(NOW);

		sut = new JwtGenerationServiceImpl(dateTimeService, tokenAuthenticationConfig);
		sut.init();
	}

	// ----------------------------------------------------------------------------------------------
	// The token a retailer keeps for itself
	// ----------------------------------------------------------------------------------------------

	@Test
	@DisplayName("An impersonation token names the user by id and is marked as impersonated")
	void testImpersonationTokenNamesTheUserById() throws ParseException {
		var claims = claimsOf(sut.toTokenForImpersonation(USER_ID).accessToken());

		assertEquals(USER_ID.asString(), claims.getStringClaim(USERID_FIELD));
		assertTrue(sut.isImpersonated(claims));
	}

	@Test
	@DisplayName("An impersonation token says nothing about the user beyond the id it is built from")
	void testImpersonationTokenCarriesNoUserName() throws ParseException {
		var claims = claimsOf(sut.toTokenForImpersonation(USER_ID).accessToken());

		assertFalse(claims.getClaims().containsValue(USER_NAME));
	}

	@Test
	@DisplayName("An impersonation token expires after the configured session time")
	void testImpersonationTokenExpiresAfterTheSessionTime() throws ParseException {
		var result = sut.toTokenForImpersonation(USER_ID);

		assertEquals(SESSION_TIME_SECONDS, result.expiresInSeconds());
		assertEquals(secondsFromNow(SESSION_TIME_SECONDS), claimsOf(result.accessToken()).getExpirationTime());
	}

	@Test
	@DisplayName("An impersonation token stands for no handoff session and cannot be renewed")
	void testImpersonationTokenCannotBeRenewed() throws ParseException {
		var claims = claimsOf(sut.toTokenForImpersonation(USER_ID).accessToken());

		assertFalse(sut.isHandoff(claims));
		assertNull(sut.getHandoffExpiration(claims));
		assertThrows(NotAuthorizedException.class, () -> sut.renewHandoffToken(claims));
	}

	@Test
	@DisplayName("A freshly minted impersonation token passes validation and resolves the user it names")
	void testMintedTokenResolvesTheUserItNames() throws JOSEException {
		var user = validate(sut.toTokenForImpersonation(USER_ID).accessToken());

		assertEquals(USER_ID.asString(), user.getId().asString());
	}

	// ----------------------------------------------------------------------------------------------
	// The token handed over to the single-page application
	// ----------------------------------------------------------------------------------------------

	@Test
	@DisplayName("A handoff token names the user by id and is marked both as impersonated and as a handoff")
	void testHandoffTokenNamesTheUserByIdAndIsMarkedAsHandoff() throws ParseException {
		var claims = claimsOf(sut.toTokenForHandoff(USER_ID).accessToken());

		assertEquals(USER_ID.asString(), claims.getStringClaim(USERID_FIELD));
		assertTrue(sut.isImpersonated(claims));
		assertTrue(sut.isHandoff(claims));
	}

	@Test
	@DisplayName("A handoff token expires after the handoff session time")
	void testHandoffTokenExpiresAfterTheHandoffSessionTime() throws ParseException {
		var result = sut.toTokenForHandoff(USER_ID);

		assertEquals(HANDOFF_SESSION_TIME_SECONDS, result.expiresInSeconds());
		assertEquals(secondsFromNow(HANDOFF_SESSION_TIME_SECONDS), claimsOf(result.accessToken()).getExpirationTime());
	}

	@Test
	@DisplayName("A handoff token names the end of the session it may not outlive")
	void testHandoffTokenNamesTheEndOfItsSession() throws ParseException {
		var claims = claimsOf(sut.toTokenForHandoff(USER_ID).accessToken());

		assertEquals(secondsFromNow(HANDOFF_MAX_SESSION_TIME_SECONDS), sut.getHandoffExpiration(claims));
	}

	@Test
	@DisplayName("Renewing a handoff token issues another token but leaves the end of the session where it was")
	void testRenewalExtendsTheTokenButNotTheSession() throws ParseException {
		var original = claimsOf(sut.toTokenForHandoff(USER_ID).accessToken());
		var renewedAfterSeconds = 10 * 60;

		when(dateTimeService.currentTimeMillis()).thenReturn(millisFromNow(renewedAfterSeconds));
		var result = sut.renewHandoffToken(original);
		var renewed = claimsOf(result.accessToken());

		assertEquals(USER_ID.asString(), renewed.getStringClaim(USERID_FIELD));
		assertTrue(sut.isHandoff(renewed));
		assertEquals(HANDOFF_SESSION_TIME_SECONDS, result.expiresInSeconds());
		assertEquals(secondsFromNow(renewedAfterSeconds + HANDOFF_SESSION_TIME_SECONDS), renewed.getExpirationTime());
		assertEquals(sut.getHandoffExpiration(original), sut.getHandoffExpiration(renewed));
	}

	@Test
	@DisplayName("A renewal late in the session does not reach past the end of it")
	void testRenewalLateInTheSessionStopsAtTheEndOfIt() throws ParseException {
		var original = claimsOf(sut.toTokenForHandoff(USER_ID).accessToken());
		var oneMinuteLeft = HANDOFF_MAX_SESSION_TIME_SECONDS - 60;

		when(dateTimeService.currentTimeMillis()).thenReturn(millisFromNow(oneMinuteLeft));
		var result = sut.renewHandoffToken(original);

		assertEquals(60, result.expiresInSeconds());
		assertEquals(secondsFromNow(HANDOFF_MAX_SESSION_TIME_SECONDS), claimsOf(result.accessToken()).getExpirationTime());
	}

	@Test
	@DisplayName("A handoff token whose session has ended cannot be renewed")
	void testRenewalAfterTheEndOfTheSessionIsRefused() throws ParseException {
		var original = claimsOf(sut.toTokenForHandoff(USER_ID).accessToken());

		when(dateTimeService.currentTimeMillis()).thenReturn(millisFromNow(HANDOFF_MAX_SESSION_TIME_SECONDS));

		assertThrows(NotAuthorizedException.class, () -> sut.renewHandoffToken(original));
	}

	@Test
	@DisplayName("A freshly minted handoff token passes validation and resolves the user it names")
	void testMintedHandoffTokenResolvesTheUserItNames() throws JOSEException {
		var user = validate(sut.toTokenForHandoff(USER_ID).accessToken());

		assertEquals(USER_ID.asString(), user.getId().asString());
	}

	// ----------------------------------------------------------------------------------------------
	// Helpers
	// ----------------------------------------------------------------------------------------------

	/**
	 * Put the token through the validation it will meet on an incoming request, which is the other half of the agreement
	 * on where a minted token names things.
	 */
	private User validate(String token) throws JOSEException {
		var dbUser = new UserImpl(USER_ID, null, null, false, false);
		lenient().when(userService.requireUserWithId(any(), any(), anyBoolean())).thenReturn(Uni.createFrom().item(dbUser));
		lenient().when(jwsVerifierMapper.get(INTERNAL_KID)).thenReturn(new MACVerifier(secretBytes()));
		var tokenHelper = new TokenHelperImpl(jwsVerifierMapper, dateTimeService, userService, sut, tokenAuthenticationConfig);

		return tokenHelper.processToken(token).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static JWTClaimsSet claimsOf(String token) throws ParseException {
		return SignedJWT.parse(token).getJWTClaimsSet();
	}

	private static byte[] secretBytes() {
		return Base64.getMimeDecoder().decode(JWT_SECRET);
	}

	private static long millisFromNow(long seconds) {
		return NOW_MILLIS + seconds * 1000L;
	}

	private static Date secondsFromNow(long seconds) {
		return new Date(millisFromNow(seconds));
	}
}
