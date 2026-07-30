package eu.tealhelix.common.web.authentication.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.lenient;

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
import eu.tealhelix.common.types.EmailAddress;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests the impersonation tokens that {@link JwtGenerationServiceImpl} mints. Such a token stands for a user towards
 * this application while it is held by a retailer, so what it does and does not say about that user matters; the last
 * test mints and then validates, because the two sides have to agree on where the user is named.
 */
@ExtendWith(MockitoExtension.class)
public class JwtGenerationServiceImplTest {
	private static final long ASYNC_WAIT_SECONDS = 30;

	private static final String INTERNAL_KID = "howibuy:1";
	private static final int SESSION_TIME_SECONDS = 3600;

	private static final String USERID_FIELD = "sub";
	private static final String USERNAME_FIELD = "preferred_username";
	private static final String CLIENT_ID_FIELD = "client_id";
	private static final String EMAIL_FIELD = "email";

	private static final String USER_ID = "518cae6a-f2b2-4454-b74d-f2404feab2f5";
	private static final String USER_NAME = "bob@krusty-krab.com";
	private static final User USER =
			new UserImpl(new UserIdImpl(USER_ID), USER_NAME, EmailAddress.of("bob@krusty-krab.com"), false, false);

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

		lenient().when(dateTimeService.currentTimeMillis()).thenReturn(NOW_MILLIS);
		lenient().when(dateTimeService.getNow()).thenReturn(NOW);

		sut = new JwtGenerationServiceImpl(dateTimeService, tokenAuthenticationConfig);
		sut.init();
	}

	@Test
	@DisplayName("An impersonation token names the user by id and is marked as impersonated")
	void testImpersonationTokenNamesTheUserById() throws ParseException {
		var claims = claimsOf(sut.toTokenForImpersonation(USER).accessToken());

		assertEquals(USER_ID, claims.getStringClaim(USERID_FIELD));
		assertTrue(sut.isImpersonated(claims));
	}

	@Test
	@DisplayName("An impersonation token says nothing about the user beyond the id it is built from")
	void testImpersonationTokenCarriesNoUserName() throws ParseException {
		var claims = claimsOf(sut.toTokenForImpersonation(USER).accessToken());

		assertFalse(claims.getClaims().containsValue(USER_NAME));
	}

	@Test
	@DisplayName("An impersonation token expires after the configured session time")
	void testImpersonationTokenExpiresAfterTheSessionTime() throws ParseException {
		var result = sut.toTokenForImpersonation(USER);

		assertEquals(SESSION_TIME_SECONDS, result.expiresInSeconds());
		assertEquals(new Date(NOW_MILLIS + SESSION_TIME_SECONDS * 1000L), claimsOf(result.accessToken()).getExpirationTime());
	}

	@Test
	@DisplayName("A freshly minted impersonation token passes validation and resolves the user it names")
	void testMintedTokenResolvesTheUserItNames() throws JOSEException {
		var dbUser = new UserImpl(new UserIdImpl(USER_ID), null, null, false, false);
		lenient().when(userService.requireUserWithId(any(), any(), anyBoolean())).thenReturn(Uni.createFrom().item(dbUser));
		lenient().when(jwsVerifierMapper.get(INTERNAL_KID)).thenReturn(new MACVerifier(secretBytes()));
		var tokenHelper = new TokenHelperImpl(jwsVerifierMapper, dateTimeService, userService, sut, tokenAuthenticationConfig);

		var token = sut.toTokenForImpersonation(USER).accessToken();
		var user = tokenHelper.processToken(token).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertEquals(USER_ID, user.getId().asString());
	}

	private static JWTClaimsSet claimsOf(String token) throws ParseException {
		return SignedJWT.parse(token).getJWTClaimsSet();
	}

	private static byte[] secretBytes() {
		return Base64.getMimeDecoder().decode(JWT_SECRET);
	}
}
