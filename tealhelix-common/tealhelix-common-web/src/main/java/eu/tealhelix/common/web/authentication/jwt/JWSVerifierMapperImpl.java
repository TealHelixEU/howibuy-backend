package eu.tealhelix.common.web.authentication.jwt;

import static com.nimbusds.jose.jwk.source.JWKSourceBuilder.DEFAULT_REFRESH_AHEAD_TIME;

import java.io.Serial;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.CachingJWKSetSource;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.events.Event;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Implementation of {@link JWSVerifierMapper}.
 * <p>
 * This uses the facilities if Nimbus to cache the retrieved data for a given amount of time.
 */
@ApplicationScoped
public class JWSVerifierMapperImpl implements JWSVerifierMapper {

	private final TokenAuthenticationConfig tokenAuthenticationConfig;

	private JWKSource<? extends SecurityContext> jwkSet;

	/**
	 * Cache the verifiers by key id; the {@code RSASSAVerifier} <em>IS</em>
	 * thread-safe, as stated in its class Javadocs.
	 * No such guarantee is given for the interface {@code JWSVerifier}.
	 */
	private ConcurrentMap<String, RSASSAVerifier> verifierMap;

	@Inject
	public JWSVerifierMapperImpl(TokenAuthenticationConfig tokenAuthenticationConfig) {
		this.tokenAuthenticationConfig = tokenAuthenticationConfig;
	}

	@PostConstruct
	void initialize() {
		jwkSet = JWKSourceBuilder.create(tokenAuthenticationConfig.getJwkUrl())
				.retrying(true)
				.outageTolerant(true)
				.rateLimited(true)
				.refreshAheadCache(DEFAULT_REFRESH_AHEAD_TIME, false, this::cacheEventListener)
//				.cache(tokenAuthenticationConfig.getJwkCacheTtl()) // TODO Utilize the configuration TTL, Max TTL
				.build();
		verifierMap = new ConcurrentHashMap<>();
	}

	private <C extends SecurityContext> void cacheEventListener(final Event<CachingJWKSetSource<C>, C> event) {
		if (event instanceof CachingJWKSetSource.RefreshCompletedEvent) {
			verifierMap.clear();
		}
	}

	@Override
	public JWSVerifier get(String kid) throws JOSEException {
		try {
			return verifierMap.computeIfAbsent(kid, this::compute);
		}
		catch( JOSEExceptionWrapper wrapper ) {
			throw wrapper.getWrapped();
		}
	}

	private RSASSAVerifier compute(String kid) {
		try {
			JWKMatcher matcher = new JWKMatcher.Builder().keyID(kid).build();
			List<JWK> keys = jwkSet.get(new JWKSelector(matcher), null);
			if( keys.size() > 1 ) {
				throw new JOSEException("found " + keys.size() + " keys for kid=" + kid);
			}
			RSASSAVerifier result = null;
			if( keys.size() == 1 ) {
				JWK jwk = keys.getFirst();
				if( !(jwk instanceof RSAKey) ) {
					throw new JOSEException("the key " + kid + " is not an RSAKey");
				}
				result = new RSASSAVerifier((RSAKey) jwk);
			}
			return result; // If null, it is not entered in the Map (ConcurrentMap/ConcurrentHashMap specs), so we are OK for memory attacks
		}
		catch( JOSEException jose ) {
			throw new JOSEExceptionWrapper(jose);
		}
	}


	private static class JOSEExceptionWrapper extends RuntimeException {

		@Serial
		private static final long serialVersionUID = 1L;

		JOSEExceptionWrapper(JOSEException cause) {
			super(cause);
		}

		JOSEException getWrapped() {
			return (JOSEException) getCause();
		}
	}
}
