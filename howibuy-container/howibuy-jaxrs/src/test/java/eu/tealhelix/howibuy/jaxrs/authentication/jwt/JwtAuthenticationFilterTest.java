package eu.tealhelix.howibuy.jaxrs.authentication.jwt;

import static eu.tealhelix.howibuy.jaxrs.authentication.RequestCatalogue.currentCatalogue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.SecurityContext;

import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.jboss.logmanager.LogContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests that {@link JwtAuthenticationFilter} hands the Catalogue a token names on to the resources of its request. The
 * request keeps its properties in a map of the test, the way the container does.
 */
@ExtendWith(MockitoExtension.class)
public class JwtAuthenticationFilterTest {
	private static final long ASYNC_WAIT_SECONDS = 30;

	private static final String TOKEN = "the-token";
	private static final CatalogueId CATALOGUE_ID = new CatalogueIdImpl("lime-fresh-gr");
	private static final User USER = new UserImpl(new UserIdImpl("518cae6a-f2b2-4454-b74d-f2404feab2f5"), null, null, false, true, Set.of());

	@Mock
	private TokenHelper tokenHelper;

	@Mock
	private ContainerRequestContext requestContext;

	@Mock
	private SecurityContext securityContext;

	private final MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();
	private final Map<String, Object> properties = new HashMap<>();

	private final List<LogRecord> logged = new ArrayList<>();

	private final Handler logHandler = new Handler() {
		@Override
		public void publish(LogRecord record) {
			logged.add(record);
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() {
		}
	};

	private JwtAuthenticationFilter sut;

	@BeforeEach
	void setUp() {
		lenient().when(requestContext.getHeaders()).thenReturn(headers);
		lenient().when(requestContext.getSecurityContext()).thenReturn(securityContext);
		lenient().when(requestContext.getProperty(anyString())).thenAnswer(invocation -> properties.get(invocation.<String>getArgument(0)));
		lenient().doAnswer(invocation -> properties.put(invocation.getArgument(0), invocation.getArgument(1)))
				.when(requestContext).setProperty(anyString(), any());
		lenient().when(tokenHelper.makeUnauthenticated()).thenReturn(new UserImpl(null, null, null, false, true, Set.of()));

		var logger = LogContext.getLogContext().getLogger(JwtAuthenticationFilter.class.getName());
		logger.addHandler(logHandler);
		logger.setUseParentHandlers(false);

		sut = new JwtAuthenticationFilter(tokenHelper);
	}

	@AfterEach
	void tearDown() {
		var logger = LogContext.getLogContext().getLogger(JwtAuthenticationFilter.class.getName());
		logger.removeHandler(logHandler);
		logger.setUseParentHandlers(true);
	}

	@Test
	@DisplayName("A request made with a token that names a Catalogue acts in that Catalogue")
	void testRequestActsInTheCatalogueOfItsToken() {
		headers.putSingle(BearerToken.AUTHORIZATION_HEADER, "Bearer " + TOKEN);
		when(tokenHelper.processToken(TOKEN)).thenReturn(Uni.createFrom().item(new Authentication(USER, CATALOGUE_ID)));

		filter();

		assertEquals(Optional.of(CATALOGUE_ID), currentCatalogue(requestContext));
	}

	@Test
	@DisplayName("A request made with a token that names no Catalogue acts in none")
	void testRequestWithATokenNamingNoCatalogueActsInNone() {
		headers.putSingle(BearerToken.AUTHORIZATION_HEADER, "Bearer " + TOKEN);
		when(tokenHelper.processToken(TOKEN)).thenReturn(Uni.createFrom().item(new Authentication(USER, null)));

		filter();

		assertEquals(Optional.empty(), currentCatalogue(requestContext));
	}

	@Test
	@DisplayName("A request made without a token acts in no Catalogue")
	void testRequestWithoutATokenActsInNoCatalogue() {
		filter();

		assertEquals(Optional.empty(), currentCatalogue(requestContext));
	}

	@Test
	@DisplayName("A request made with a rejected token is not authorized, and the reason is logged")
	void testRequestWithARejectedTokenIsNotAuthorizedAndTheReasonIsLogged() {
		headers.putSingle(BearerToken.AUTHORIZATION_HEADER, "Bearer " + TOKEN);
		when(tokenHelper.processToken(TOKEN)).thenReturn(Uni.createFrom().failure(new TokenHelperException("JWT expired at the dawn of time")));

		var failure = sut.filter(requestContext)
				.subscribe().withSubscriber(UniAssertSubscriber.create())
				.awaitFailure(Duration.ofSeconds(ASYNC_WAIT_SECONDS))
				.getFailure();

		assertInstanceOf(NotAuthorizedException.class, failure);
		assertEquals(List.of("Bearer token rejected, reason: JWT expired at the dawn of time"), loggedAt(Level.INFO));
	}

	private List<String> loggedAt(Level level) {
		return logged.stream()
				.filter(record -> record.getLevel().intValue() == level.intValue())
				.map(LogRecord::getMessage)
				.toList();
	}

	private void filter() {
		sut.filter(requestContext).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}
}
