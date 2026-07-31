package eu.tealhelix.common.web.authentication.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.util.DateUtils;
import eu.tealhelix.common.services.generic.DateTimeService;
import eu.tealhelix.common.services.generic.UserService;
import eu.tealhelix.common.types.EmailAddress;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests the validation {@link TokenHelperImpl} applies before it maps a token onto a {@code User}.
 * <p>
 * Two token families reach {@code processToken}: tokens the IDM issued (RS256, key id served by the IDM's JWK set)
 * and tokens this application issued itself (HS256, the configured internal key id). The families carry different
 * claims and therefore get different validation, which is what most of these tests pin down.
 */
@ExtendWith(MockitoExtension.class)
public class TokenHelperImplTest {
	private static final long ASYNC_WAIT_SECONDS = 30;

	private static final String IDM_KID = "idm-key-1";
	private static final String INTERNAL_KID = "howibuy:1";
	private static final String EXPECTED_ISSUER = "http://localhost:8280/realms/tealhelix";
	private static final String ANOTHER_ISSUER = "http://localhost:8280/realms/somewhere-else";
	private static final String SPA_CLIENT = "howibuy";
	private static final String RETAILER_CLIENT = "lime_fresh";
	private static final String UNRELATED_CLIENT = "claimsbuster";
	private static final String ACCESS_TOKEN_TYPE = "Bearer";

	private static final String USERNAME_FIELD = "preferred_username";
	private static final String USERID_FIELD = "sub";
	private static final String CLIENT_ID_FIELD = "client_id";
	private static final String EMAIL_FIELD = "email";

	private static final String IDM_SUB = "2e788895-0503-4777-a7bd-24e5d61db5b1";
	private static final String INTERNAL_USER_ID = "518cae6a-f2b2-4454-b74d-f2404feab2f5";
	private static final String USER_NAME = "bob@krusty-krab.com";
	private static final EmailAddress USER_EMAIL = EmailAddress.of("bob@krusty-krab.com");
	private static final EmailAddress ANOTHER_EMAIL = EmailAddress.of("plankton@chum-bucket.com");
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 12, 0, 0);

	/** A 512-bit secret, the shape {@code config.jwt.secret} has; only ever used by this test. */
	private static final String JWT_SECRET =
			"nRvyYC4soFxBdZ-F-5Nnzz5USXstR1YylsTd-mA0aKtI9HUlriGrtkf-TiuDapkLiUCogO3JOK7kwZisrHp6wA";

	private static RSAKey idmKey;

	@Mock
	private JWSVerifierMapper jwsVerifierMapper;

	@Mock
	private DateTimeService dateTimeService;

	@Mock
	private UserService userService;

	@Mock
	private TokenAuthenticationConfig tokenAuthenticationConfig;

	private TokenHelperImpl sut;

	@BeforeEach
	void setUp() throws JOSEException {
		if (idmKey == null) {
			idmKey = new RSAKeyGenerator(2048).keyID(IDM_KID).generate();
		}

		lenient().when(tokenAuthenticationConfig.getUsernameFieldInJwt()).thenReturn(USERNAME_FIELD);
		lenient().when(tokenAuthenticationConfig.getUserIdFieldInJwt()).thenReturn(USERID_FIELD);
		lenient().when(tokenAuthenticationConfig.getClientIdFieldInJwt()).thenReturn(CLIENT_ID_FIELD);
		lenient().when(tokenAuthenticationConfig.getEmailFieldInJwt()).thenReturn(EMAIL_FIELD);
		lenient().when(tokenAuthenticationConfig.getInternalKeyId()).thenReturn(INTERNAL_KID);
		lenient().when(tokenAuthenticationConfig.getJwtSecret()).thenReturn(JWT_SECRET);
		lenient().when(tokenAuthenticationConfig.getExpectedIssuer()).thenReturn(EXPECTED_ISSUER);
		lenient().when(tokenAuthenticationConfig.getAllowedUserClients()).thenReturn(List.of(SPA_CLIENT));

		lenient().when(dateTimeService.getNow()).thenReturn(NOW);

		lenient().when(jwsVerifierMapper.get(IDM_KID)).thenReturn(new RSASSAVerifier(idmKey));
		lenient().when(jwsVerifierMapper.get(INTERNAL_KID)).thenReturn(new MACVerifier(secretBytes()));

		var jwtGenerationService = new JwtGenerationServiceImpl(dateTimeService, tokenAuthenticationConfig);
		jwtGenerationService.init();

		sut = new TokenHelperImpl(jwsVerifierMapper, dateTimeService, userService, jwtGenerationService, tokenAuthenticationConfig);
	}

	// ----------------------------------------------------------------------------------------------
	// Tokens issued by the IDM
	// ----------------------------------------------------------------------------------------------

	@Test
	@DisplayName("A token issued by an unexpected issuer is rejected")
	void testUnexpectedIssuerIsRejected() throws JOSEException {
		var token = signWithIdmKey(idmUserClaims().issuer(ANOTHER_ISSUER).build());
		assertRejected(token);
	}

	@Test
	@DisplayName("A token issued to a client of the realm that this application does not accept is rejected")
	void testUnrelatedClientIsRejected() throws JOSEException {
		var token = signWithIdmKey(idmUserClaims().claim("azp", UNRELATED_CLIENT).build());
		assertRejected(token);
	}

	@Test
	@DisplayName("A token that does not say which client it was issued to is rejected")
	void testMissingAuthorizedPartyIsRejected() throws JOSEException {
		var token = signWithIdmKey(idmUserClaims().claim("azp", null).build());
		assertRejected(token);
	}

	@Test
	@DisplayName("An ID token presented in place of an access token is rejected")
	void testIdTokenIsRejected() throws JOSEException {
		var token = signWithIdmKey(idmUserClaims().claim("typ", "ID").build());
		assertRejected(token);
	}

	@Test
	@DisplayName("A token that carries no expiration time is rejected")
	void testMissingExpirationIsRejected() throws JOSEException {
		var token = signWithIdmKey(idmUserClaims().expirationTime(null).build());
		assertRejected(token);
	}

	@Test
	@DisplayName("A token that carries no subject is rejected")
	void testMissingSubjectIsRejected() throws JOSEException {
		var token = signWithIdmKey(idmUserClaims().subject(null).build());
		assertRejected(token);
	}

	@Test
	@DisplayName("An expired token is rejected")
	void testExpiredTokenIsRejected() throws JOSEException {
		var token = signWithIdmKey(idmUserClaims().expirationTime(minutesFromNow(-1)).build());
		assertRejected(token);
	}

	@Test
	@DisplayName("A token bearing an IDM key id but signed with the internal algorithm is rejected")
	void testIdmTokenWithWrongAlgorithmIsRejected() throws JOSEException {
		var header = new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(IDM_KID).build();
		var jwt = new SignedJWT(header, idmUserClaims().build());
		jwt.sign(new MACSigner(secretBytes()));
		assertRejected(jwt.serialize());
	}

	@Test
	@DisplayName("A valid user access token resolves the user through the IDM id")
	void testValidUserTokenIsAccepted() throws JOSEException {
		var dbUser = new UserImpl(new UserIdImpl(INTERNAL_USER_ID), USER_NAME, USER_EMAIL, false, false);
		lenient().when(userService.requireUserFromValidIdmId(IDM_SUB, USER_NAME, false))
				.thenReturn(Uni.createFrom().item(dbUser));

		var user = await(signWithIdmKey(idmUserClaims().build()));

		assertEquals(INTERNAL_USER_ID, user.getId().asString());
		assertFalse(user.isService());
	}

	@Test
	@DisplayName("A user token is accepted although its email differs from the one on record")
	void testUserTokenWithDifferentEmailIsAccepted() throws JOSEException {
		var dbUser = new UserImpl(new UserIdImpl(INTERNAL_USER_ID), USER_NAME, ANOTHER_EMAIL, false, false);
		lenient().when(userService.requireUserFromValidIdmId(IDM_SUB, USER_NAME, false))
				.thenReturn(Uni.createFrom().item(dbUser));

		var user = await(signWithIdmKey(idmUserClaims().build()));

		assertEquals(INTERNAL_USER_ID, user.getId().asString());
	}

	@Test
	@DisplayName("A token issued for a user to a client that users may not sign in through is rejected")
	void testUserTokenFromRetailerClientIsRejected() throws JOSEException {
		var token = signWithIdmKey(idmUserClaims().claim("azp", RETAILER_CLIENT).build());
		assertRejected(token);
	}

	@Test
	@DisplayName("A service account token is accepted although its client is not one users may sign in through")
	void testValidServiceTokenIsAccepted() throws JOSEException {
		var claims = idmUserClaims()
				.claim("azp", RETAILER_CLIENT)
				.claim(CLIENT_ID_FIELD, RETAILER_CLIENT)
				.build();

		var user = await(signWithIdmKey(claims));

		assertTrue(user.isService());
		assertEquals(RETAILER_CLIENT, user.getName());
		verify(userService, never()).requireUserFromValidIdmId(any(), any(), anyBoolean());
		verify(userService, never()).requireUserWithId(any(), any(), anyBoolean());
	}

	// ----------------------------------------------------------------------------------------------
	// Tokens issued by this application
	// ----------------------------------------------------------------------------------------------

	@Test
	@DisplayName("A valid impersonation token resolves the user through the internal user id")
	void testValidImpersonationTokenIsAccepted() throws JOSEException {
		var dbUser = new UserImpl(new UserIdImpl(INTERNAL_USER_ID), null, null, false, false);
		lenient().when(userService.requireUserWithId(any(), any(), anyBoolean()))
				.thenReturn(Uni.createFrom().item(dbUser));

		var user = await(signWithInternalKey(impersonationClaims().build()));

		assertEquals(INTERNAL_USER_ID, user.getId().asString());
		verify(userService, never()).requireUserFromValidIdmId(any(), any(), anyBoolean());
	}

	@Test
	@DisplayName("An impersonation token is accepted for a user who has an email address on record")
	void testImpersonationTokenIsAcceptedForUserWithEmail() throws JOSEException {
		var dbUser = new UserImpl(new UserIdImpl(INTERNAL_USER_ID), null, USER_EMAIL, false, false);
		lenient().when(userService.requireUserWithId(any(), any(), anyBoolean()))
				.thenReturn(Uni.createFrom().item(dbUser));

		var user = await(signWithInternalKey(impersonationClaims().build()));

		assertEquals(INTERNAL_USER_ID, user.getId().asString());
	}

	@Test
	@DisplayName("A token signed with the internal key but not marked as impersonated is rejected")
	void testInternalTokenWithoutImpersonationMarkerIsRejected() throws JOSEException {
		var claims = new JWTClaimsSet.Builder()
				.claim(USERID_FIELD, INTERNAL_USER_ID)
				.expirationTime(minutesFromNow(15))
				.build();
		assertRejected(signWithInternalKey(claims));
	}

	@Test
	@DisplayName("A token bearing the internal key id but signed with the IDM algorithm is rejected")
	void testInternalTokenWithWrongAlgorithmIsRejected() throws JOSEException {
		var header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(INTERNAL_KID).build();
		var jwt = new SignedJWT(header, impersonationClaims().build());
		jwt.sign(new RSASSASigner(idmKey));
		assertRejected(jwt.serialize());
	}

	@Test
	@DisplayName("A valid handoff token resolves the user through the internal user id")
	void testValidHandoffTokenIsAccepted() throws JOSEException {
		var dbUser = new UserImpl(new UserIdImpl(INTERNAL_USER_ID), null, null, false, false);
		lenient().when(userService.requireUserWithId(any(), any(), anyBoolean()))
				.thenReturn(Uni.createFrom().item(dbUser));

		var user = await(signWithInternalKey(handoffClaims().build()));

		assertEquals(INTERNAL_USER_ID, user.getId().asString());
	}

	@Test
	@DisplayName("A handoff token that does not name the end of its session is rejected")
	void testHandoffTokenWithoutTheEndOfItsSessionIsRejected() throws JOSEException {
		var claims = impersonationClaims().claim("handoff", true).build();
		assertRejected(signWithInternalKey(claims));
	}

	@Test
	@DisplayName("A handoff token presented after the end of its session is rejected, however long it says it lives")
	void testHandoffTokenAfterTheEndOfItsSessionIsRejected() throws JOSEException {
		var claims = handoffClaims()
				.claim("handoff_exp", DateUtils.toSecondsSinceEpoch(minutesFromNow(-1)))
				.build();
		assertRejected(signWithInternalKey(claims));
	}

	// ----------------------------------------------------------------------------------------------
	// Helpers
	// ----------------------------------------------------------------------------------------------

	/** The claims of a valid access token that the IDM issued to the single-page application for a user. */
	private JWTClaimsSet.Builder idmUserClaims() {
		return new JWTClaimsSet.Builder()
				.issuer(EXPECTED_ISSUER)
				.claim("azp", SPA_CLIENT)
				.claim("typ", ACCESS_TOKEN_TYPE)
				.subject(IDM_SUB)
				.claim(USERNAME_FIELD, USER_NAME)
				.claim(EMAIL_FIELD, USER_EMAIL.asString())
				.expirationTime(minutesFromNow(5));
	}

	/** The claims of a valid impersonation token, as {@code JwtGenerationService} mints them. */
	private JWTClaimsSet.Builder impersonationClaims() {
		return new JWTClaimsSet.Builder()
				.claim(USERID_FIELD, INTERNAL_USER_ID)
				.claim("impersonated", true)
				.expirationTime(minutesFromNow(15));
	}

	/** The claims of a valid handoff token, as {@code JwtGenerationService} mints them. */
	private JWTClaimsSet.Builder handoffClaims() {
		return impersonationClaims()
				.claim("handoff", true)
				.claim("handoff_exp", DateUtils.toSecondsSinceEpoch(minutesFromNow(8 * 60)));
	}

	private String signWithIdmKey(JWTClaimsSet claims) throws JOSEException {
		var header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(IDM_KID).build();
		var jwt = new SignedJWT(header, claims);
		jwt.sign(new RSASSASigner(idmKey));
		return jwt.serialize();
	}

	private String signWithInternalKey(JWTClaimsSet claims) throws JOSEException {
		var header = new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(INTERNAL_KID).build();
		var jwt = new SignedJWT(header, claims);
		jwt.sign(new MACSigner(secretBytes()));
		return jwt.serialize();
	}

	private static byte[] secretBytes() {
		return Base64.getMimeDecoder().decode(JWT_SECRET);
	}

	private static Date minutesFromNow(long minutes) {
		return Date.from(NOW.plusMinutes(minutes).atZone(ZoneId.systemDefault()).toInstant());
	}

	private User await(String token) {
		return sut.processToken(token).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private Throwable failureOf(String token) {
		return sut.processToken(token)
				.subscribe().withSubscriber(UniAssertSubscriber.create())
				.awaitFailure(Duration.ofSeconds(ASYNC_WAIT_SECONDS))
				.getFailure();
	}

	private void assertRejected(String token) {
		assertInstanceOf(TokenHelperException.class, failureOf(token));
	}
}
