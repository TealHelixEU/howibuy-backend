package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.howibuy.v1.types.RetailerIdTestUtils.hasRetailerId;
import static eu.tealhelix.howibuy.v1.types.RetailerIdTestUtils.matchesRetailerId;
import static eu.tealhelix.common.v1.types.UserIdTestUtils.matchesHasUserId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Duration;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import eu.tealhelix.howibuy.dao.ConsentDao;
import eu.tealhelix.howibuy.dao.CorrelationIdDao;
import eu.tealhelix.howibuy.dao.RetailerDao;
import eu.tealhelix.howibuy.dao.UserProfileDao;
import eu.tealhelix.common.dao.EntityNotFoundException;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.services.authz.impl.TealHelixAuthorizationImpl;
import eu.tealhelix.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.tealhelix.common.types.EmailAddress;
import eu.tealhelix.common.types.authorization.NotAuthorizedException;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.UserId;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@EnableAutoWeld
@AddBeanClasses(TealHelixAuthorizationImpl.class)
@ExtendWith(MockitoExtension.class)
public class UserImpersonationServiceImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final UserId USER_ID_REGULAR = new UserIdImpl("2e788895-0503-4777-a7bd-24e5d61db5b1");
	private static final UserId USER_ID_RETAILER = new UserIdImpl("518cae6a-f2b2-4454-b74d-f2404feab2f5");
	private static final User RETAILER_USER = new UserImpl(USER_ID_RETAILER, null, null, false, true);
	private static final User REGULAR_USER = new UserImpl(USER_ID_REGULAR, null, null, false, false);
	private static final User USER_BOB = new UserImpl(USER_ID_REGULAR, "Bob Squarepants", EmailAddress.of("bob@krusty-krab.com"), false, false);
	private static final String CORRELATION_ID = "abc";

	@Produces
	@Mock
	CorrelationIdDao mockCorrelationIdDao;

	@Produces
	@Mock
	ConsentDao mockConsentDao;

	@Produces
	@Mock
	UserProfileDao mockUserProfileDao;

	@Produces
	@Mock
	RetailerDao mockRetailerDao;

	@Produces
	@RegisterExtension
	private MockReactivePersistenceContextFactory mockReactivePersistenceContextFactory = new MockReactivePersistenceContextFactory();

	@Inject
	UserImpersonationServiceImpl sut;

	@Test
	@DisplayName("When invoking impersonateUserAsRetailer from a non-retailer account, we get a NotAuthorizedException")
	void testImpersonateUserAsRetailerNotAsServiceUser() {
		var t = sut.impersonateUserAsRetailer(REGULAR_USER, CORRELATION_ID).subscribe().withSubscriber(UniAssertSubscriber.create())
				.awaitFailure(Duration.ofSeconds(ASYNC_WAIT_SECONDS)).getFailure();
		assertInstanceOf(NotAuthorizedException.class, t);
	}

	@Test
	@DisplayName("When invoking impersonateUserAsRetailer and the retailer is not in the database, we get a NotAuthorizedException")
	void testImpersonateUserAsRetailerRetailerDoesNotExist() {
		mockRetailerAbsent(RETAILER_USER);
		var t = sut.impersonateUserAsRetailer(RETAILER_USER, CORRELATION_ID).subscribe().withSubscriber(UniAssertSubscriber.create())
				.awaitFailure(Duration.ofSeconds(ASYNC_WAIT_SECONDS)).getFailure();
		assertInstanceOf(NotAuthorizedException.class, t);
	}

	@Test
	@DisplayName("When invoking impersonateUserAsRetailer and the retailer is deactivated, we get a NotAuthorizedException")
	void testImpersonateUserAsRetailerRetailerInactive() {
		mockRetailerInactive(RETAILER_USER);
		var t = sut.impersonateUserAsRetailer(RETAILER_USER, CORRELATION_ID).subscribe().withSubscriber(UniAssertSubscriber.create())
				.awaitFailure(Duration.ofSeconds(ASYNC_WAIT_SECONDS)).getFailure();
		assertInstanceOf(NotAuthorizedException.class, t);
	}

	@Test
	@DisplayName("When invoking impersonateUserAsRetailer, the correlation id exists and the user has not consented, we get a NotAuthorizedException")
	void testImpersonateUserAsRetailerCorrelationIdExistsNoConsent() {
		mockRetailerActive(RETAILER_USER);
		mockExistingCorrelationId(RETAILER_USER, USER_BOB);
		mockNoConsent(USER_BOB, RETAILER_USER);
		var t = sut.impersonateUserAsRetailer(RETAILER_USER, CORRELATION_ID).subscribe().withSubscriber(UniAssertSubscriber.create())
				.awaitFailure(Duration.ofSeconds(ASYNC_WAIT_SECONDS)).getFailure();
		assertInstanceOf(NotAuthorizedException.class, t);
	}

	@Test
	@DisplayName("When invoking impersonateUserAsRetailer, the correlation id exists and the user has consented, we get a User object")
	void testImpersonateUserAsRetailerCorrelationIdExistsConsent() {
		mockRetailerActive(RETAILER_USER);
		mockExistingCorrelationId(RETAILER_USER, USER_BOB);
		mockConsent(USER_BOB, RETAILER_USER);
		mockToUser();
		var u = sut.impersonateUserAsRetailer(RETAILER_USER, CORRELATION_ID)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertNotNull(u);
		assertEquals(USER_ID_REGULAR, u.getId());
	}

	@Test
	@DisplayName("When invoking impersonateUserAsRetailer and the correlation id does not exist, we create the user, consent and correlation")
	void testImpersonateUserAsRetailerCorrelationIdDoesNotExist() {
		mockRetailerActive(RETAILER_USER);
		mockNotExistingCorrelationId(RETAILER_USER);
		mockAutoUserCreation();
		mockUpdateConsent(USER_BOB, RETAILER_USER, true);
		mockCorrelationIdCreation(RETAILER_USER);
		var u = sut.impersonateUserAsRetailer(RETAILER_USER, CORRELATION_ID)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertNotNull(u);
		assertEquals(USER_ID_REGULAR, u.getId());
	}

	private void mockRetailerActive(User retailerUser) {
		when(mockRetailerDao.findActiveFlag(any(ReactivePersistenceContext.class), matchesRetailerId(retailerUser)))
				.thenReturn(Uni.createFrom().item(true));
	}

	private void mockRetailerInactive(User retailerUser) {
		when(mockRetailerDao.findActiveFlag(any(ReactivePersistenceContext.class), matchesRetailerId(retailerUser)))
				.thenReturn(Uni.createFrom().item(false));
	}

	private void mockRetailerAbsent(User retailerUser) {
		when(mockRetailerDao.findActiveFlag(any(ReactivePersistenceContext.class), matchesRetailerId(retailerUser)))
				.thenReturn(Uni.createFrom().nullItem());
	}

	private void mockNotExistingCorrelationId(User retailerUser) {
		when(mockCorrelationIdDao.requireByRetailerAndCorrelationId(any(ReactivePersistenceContext.class), matchesRetailerId(retailerUser), eq(CORRELATION_ID)))
				.thenReturn(Uni.createFrom().failure(new EntityNotFoundException("no correlation")));
	}

	private void mockExistingCorrelationId(User retailerUser, User correlatedUser) {
		when(mockCorrelationIdDao.requireByRetailerAndCorrelationId(any(ReactivePersistenceContext.class), matchesRetailerId(retailerUser), eq(CORRELATION_ID)))
				.thenReturn(Uni.createFrom().item(correlatedUser));
	}

	private void mockCorrelationIdCreation(User retailerUser) {
		when(mockCorrelationIdDao.createCorrelation(any(ReactivePersistenceTxContext.class), matchesRetailerId(retailerUser), eq(CORRELATION_ID), matchesHasUserId(USER_ID_REGULAR)))
				.thenReturn(Uni.createFrom().voidItem());
	}

	private void mockNoConsent(User user, User retailer) {
		when(mockConsentDao.hasConsentedToRetailer(any(ReactivePersistenceContext.class), matchesHasUserId(user), hasRetailerId(retailer)))
				.thenReturn(Uni.createFrom().item(false));
	}

	private void mockConsent(User user, User retailer) {
		when(mockConsentDao.hasConsentedToRetailer(any(ReactivePersistenceContext.class), matchesHasUserId(user), hasRetailerId(retailer)))
				.thenReturn(Uni.createFrom().item(true));
	}

	private void mockUpdateConsent(User user, User retailer, boolean flag) {
		when(mockConsentDao.updateConsentToRetailer(any(ReactivePersistenceTxContext.class), matchesHasUserId(user), hasRetailerId(retailer), eq(flag)))
				.thenReturn(Uni.createFrom().item(false));
	}

	private void mockToUser() {
		when(mockUserProfileDao.toUser(any())).thenAnswer(iom -> {
			var userId = (UserId) iom.getArgument(0);
			return new UserImpl(userId, null, null, false, false);
		});
	}

	private void mockAutoUserCreation() {
		when(mockUserProfileDao.createAutoUser(any(ReactivePersistenceTxContext.class)))
				.thenReturn(Uni.createFrom().item(new UserImpl(USER_ID_REGULAR, null, null, false, false)));
	}
}
