package eu.tealhelix.howibuy.services.v1.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import eu.tealhelix.common.services.authz.impl.TealHelixAuthorizationImpl;
import eu.tealhelix.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.tealhelix.common.types.authorization.NotAuthenticatedException;
import eu.tealhelix.common.types.authorization.NotAuthorizedException;
import eu.tealhelix.common.types.entity.NotFoundException;
import eu.tealhelix.common.types.validation.BadInputValueException;
import eu.tealhelix.common.v1.model.Role;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.howibuy.dao.ArchetypeProductDao;
import eu.tealhelix.howibuy.dao.ClassificationDao;
import eu.tealhelix.howibuy.services.model.ImmutableProductClassificationInput;
import eu.tealhelix.howibuy.services.model.ImmutableProductToClassify;
import eu.tealhelix.howibuy.services.v1.classification.impl.ClassificationResolutionImpl;
import eu.tealhelix.howibuy.services.v1.types.ArchetypeMatchOutcome;
import eu.tealhelix.howibuy.services.v1.types.InputHashConflictException;
import eu.tealhelix.howibuy.services.v1.types.LlmClassification;
import eu.tealhelix.howibuy.v1.model.Classification;
import eu.tealhelix.howibuy.v1.model.ClassificationAttempt;
import eu.tealhelix.howibuy.v1.model.ClassificationAttemptInput;
import eu.tealhelix.howibuy.v1.model.ClassificationToReview;
import eu.tealhelix.howibuy.v1.model.ImmutableClassification;
import eu.tealhelix.howibuy.v1.model.ImmutableClassificationAttempt;
import eu.tealhelix.howibuy.v1.model.ImmutableClassificationAttemptInput;
import eu.tealhelix.howibuy.v1.model.ImmutableClassificationToReview;
import eu.tealhelix.howibuy.v1.model.ImmutableMatchDiagnostics;
import eu.tealhelix.howibuy.v1.model.ImmutablePendingProduct;
import eu.tealhelix.howibuy.v1.model.MatchDiagnostics;
import eu.tealhelix.howibuy.v1.model.PendingProduct;
import eu.tealhelix.howibuy.v1.model.ProductBaseData;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.Classifier;
import eu.tealhelix.howibuy.v1.types.RetailerProductId;
import eu.tealhelix.howibuy.v1.types.impl.ArchetypeProductIdImpl;
import eu.tealhelix.howibuy.v1.types.impl.RetailerProductIdImpl;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.jboss.weld.junit5.auto.ExcludeBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Which callers may classify or read the Classifications to review, which attempts are refused before anything is
 * stored, and the order of the steps of recording an attempt, with the database mocked. The LLM classifies with the
 * matching mocked, whose descent is covered by {@code ArchetypeMatchingTest}. How the Classification is
 * resolved is covered by {@code ClassificationResolutionImplTest}, and what the steps store by
 * {@code ClassificationDaoImplTest}.
 */
@EnableAutoWeld
@AddBeanClasses({TealHelixAuthorizationImpl.class, ClassificationResolutionImpl.class})
@ExtendWith(MockitoExtension.class)
public class ClassificationServiceImplTest {
	private static final Duration WAIT = Duration.ofSeconds(300);

	private static final User CATALOGUE_SYNC_USER = new UserImpl(new UserIdImpl("518cae6a-f2b2-4454-b74d-f2404feab2f5"), null, null, false, false, Set.of(Role.CATALOGUE_SYNC));
	private static final User OPERATOR_USER = new UserImpl(new UserIdImpl("2e788895-0503-4777-a7bd-24e5d61db5b1"), null, null, false, true, Set.of(Role.OPERATOR));
	private static final User RETAILER_USER = new UserImpl(new UserIdImpl("518cae6a-f2b2-4454-b74d-f2404feab2f5"), null, null, false, false, Set.of(Role.RETAILER));

	private static final RetailerProductId PRODUCT = new RetailerProductIdImpl("7c1e2f4a-5b6d-4e8f-9a0b-1c2d3e4f5a61");
	private static final ArchetypeProductId YOGHURT = new ArchetypeProductIdImpl("1b0a5a3e-4f7e-4d3f-9a51-0c2b6f1e7a01");
	private static final ArchetypeProductId FETA = new ArchetypeProductIdImpl("1b0a5a3e-4f7e-4d3f-9a51-0c2b6f1e7a02");
	private static final String LLM_VERSION = "llama3.1:8b/prompt-1/glossary-1";

	@Produces
	@Mock
	ClassificationDao mockClassificationDao;

	@Produces
	@Mock
	ArchetypeProductDao mockArchetypeProductDao;

	@Produces
	@ExcludeBean
	@Mock
	ArchetypeMatching mockArchetypeMatching;

	@Produces
	@ConfigProperty(name = ClassificationServiceImpl.LLM_VERSION_KEY)
	String llmVersion = LLM_VERSION;

	@Produces
	@RegisterExtension
	private MockReactivePersistenceContextFactory mockReactivePersistenceContextFactory = new MockReactivePersistenceContextFactory();

	@Inject
	ClassificationServiceImpl sut;

	@Test
	void theCatalogueSyncAndOperatorsFindThePendingProductsOutsideATransaction() {
		var pending = List.<PendingProduct>of(ImmutablePendingProduct.builder().id(PRODUCT).inputHash("h1").addLanguages("el").build());
		when(mockClassificationDao.findPending(any(), eq(1000))).thenReturn(Uni.createFrom().item(pending));

		assertEquals(pending, sut.findPending(CATALOGUE_SYNC_USER, 1000).await().atMost(WAIT));
		assertEquals(pending, sut.findPending(OPERATOR_USER, 1000).await().atMost(WAIT));
		assertTrue(mockReactivePersistenceContextFactory.getOpenedTransactions().isEmpty(), "no transaction opened");
	}

	@Test
	void onlyTheCatalogueSyncAndOperatorsFindThePendingProducts() {
		assertThrows(NotAuthenticatedException.class, () -> sut.findPending(null, 10));
		assertThrows(NotAuthorizedException.class, () -> sut.findPending(RETAILER_USER, 10));
		verifyNoInteractions(mockClassificationDao);
	}

	@Test
	void thePendingProductsAreFoundFromOneToAThousandAtATime() {
		assertBadInput("limit", "It must be from 1 to 1000", assertThrows(BadInputValueException.class, () -> sut.findPending(OPERATOR_USER, 0)));
		assertBadInput("limit", "It must be from 1 to 1000", assertThrows(BadInputValueException.class, () -> sut.findPending(OPERATOR_USER, 1001)));
		verifyNoInteractions(mockClassificationDao);
	}

	@Test
	void theCatalogueSyncAndOperatorsFindTheClassificationsToReviewOutsideATransaction() {
		var toReview = List.<ClassificationToReview>of(ImmutableClassificationToReview.builder()
				.id(PRODUCT)
				.putNames("el", "Γιαούρτι")
				.classification(ImmutableClassification.builder().archetypeProductId(YOGHURT).inputHash("h1").isNeedsReview(true).resolvedAt(LocalDateTime.of(2026, 10, 22, 12, 0)).build())
				.addAttempts(attempt("el", Classifier.EXPERT, YOGHURT, "h1", LocalDateTime.of(2026, 10, 22, 12, 0)))
				.build());
		when(mockClassificationDao.findToReview(any(), eq(1000))).thenReturn(Uni.createFrom().item(toReview));

		assertEquals(toReview, sut.findToReview(CATALOGUE_SYNC_USER, 1000).await().atMost(WAIT));
		assertEquals(toReview, sut.findToReview(OPERATOR_USER, 1000).await().atMost(WAIT));
		assertTrue(mockReactivePersistenceContextFactory.getOpenedTransactions().isEmpty(), "no transaction opened");
	}

	@Test
	void onlyTheCatalogueSyncAndOperatorsFindTheClassificationsToReview() {
		assertThrows(NotAuthenticatedException.class, () -> sut.findToReview(null, 10));
		assertThrows(NotAuthorizedException.class, () -> sut.findToReview(RETAILER_USER, 10));
		verifyNoInteractions(mockClassificationDao);
	}

	@Test
	void theClassificationsToReviewAreFoundFromOneToAThousandAtATime() {
		assertBadInput("limit", "It must be from 1 to 1000", assertThrows(BadInputValueException.class, () -> sut.findToReview(OPERATOR_USER, 0)));
		assertBadInput("limit", "It must be from 1 to 1000", assertThrows(BadInputValueException.class, () -> sut.findToReview(OPERATOR_USER, 1001)));
		verifyNoInteractions(mockClassificationDao);
	}

	@Test
	void anAttemptIsRecordedAndTheClassificationResolvedInOneTransactionWithTheProductLocked() {
		var attempt = attemptInput("en", Classifier.EXPERT, FETA, "h1");
		givenTheProduct("h1", Set.of("el", "en"), "el");
		when(mockArchetypeProductDao.requireProductExists(any(), eq(FETA))).thenReturn(Uni.createFrom().voidItem());
		when(mockClassificationDao.recordAttempt(any(), eq(PRODUCT), any())).thenReturn(Uni.createFrom().voidItem());
		var recorded = List.of(
				attempt("el", Classifier.EXPERT, YOGHURT, "h1", LocalDateTime.of(2026, 10, 22, 12, 0)),
				attempt("en", Classifier.EXPERT, FETA, "h1", LocalDateTime.of(2026, 10, 22, 12, 1)));
		when(mockClassificationDao.findAttempts(any(), eq(PRODUCT), eq("h1"))).thenReturn(Uni.createFrom().item(recorded));
		when(mockClassificationDao.saveClassification(any(), eq(PRODUCT), any())).thenReturn(Uni.createFrom().voidItem());

		sut.recordAttempt(OPERATOR_USER, PRODUCT, attempt).await().atMost(WAIT);

		assertEquals(1, mockReactivePersistenceContextFactory.getOpenedTransactions().size(), "one transaction");
		var tx = mockReactivePersistenceContextFactory.getOpenedTransactions().getFirst();
		var attemptCaptor = ArgumentCaptor.forClass(ClassificationAttempt.class);
		var classificationCaptor = ArgumentCaptor.forClass(Classification.class);
		var inOrder = inOrder(mockClassificationDao, mockArchetypeProductDao);
		inOrder.verify(mockClassificationDao).findLockedProductToClassify(tx, PRODUCT);
		inOrder.verify(mockArchetypeProductDao).requireProductExists(tx, FETA);
		inOrder.verify(mockClassificationDao).recordAttempt(eq(tx), eq(PRODUCT), attemptCaptor.capture());
		inOrder.verify(mockClassificationDao).findAttempts(tx, PRODUCT, "h1");
		inOrder.verify(mockClassificationDao).saveClassification(eq(tx), eq(PRODUCT), classificationCaptor.capture());
		inOrder.verifyNoMoreInteractions();
		var createdAt = attemptCaptor.getValue().getCreatedAt();
		assertEquals(ImmutableClassificationAttempt.builder().from(attempt).createdAt(createdAt).build(), attemptCaptor.getValue());
		assertEquals(
				ImmutableClassification.builder().archetypeProductId(YOGHURT).inputHash("h1").isNeedsReview(true).resolvedAt(createdAt).build(),
				classificationCaptor.getValue(),
				"the attempts disagree, so the one in the Retailer's language resolves the Classification for review");
	}

	@Test
	void anAttemptThatNoArchetypeMatchesHasNoArchetypeToCheck() {
		givenTheProduct("h1", Set.of("el"), null);
		when(mockClassificationDao.recordAttempt(any(), eq(PRODUCT), any())).thenReturn(Uni.createFrom().voidItem());
		when(mockClassificationDao.findAttempts(any(), eq(PRODUCT), eq("h1")))
				.thenReturn(Uni.createFrom().item(List.of(attempt("el", Classifier.MANUAL, null, "h1", LocalDateTime.of(2026, 10, 22, 12, 0)))));
		when(mockClassificationDao.saveClassification(any(), eq(PRODUCT), any())).thenReturn(Uni.createFrom().voidItem());

		sut.recordAttempt(CATALOGUE_SYNC_USER, PRODUCT, attemptInput("el", Classifier.MANUAL, null, "h1")).await().atMost(WAIT);

		verifyNoInteractions(mockArchetypeProductDao);
		var classificationCaptor = ArgumentCaptor.forClass(Classification.class);
		verify(mockClassificationDao).saveClassification(any(), eq(PRODUCT), classificationCaptor.capture());
		assertNull(classificationCaptor.getValue().getArchetypeProductId());
		assertFalse(classificationCaptor.getValue().isNeedsReview());
	}

	@Test
	void onlyTheCatalogueSyncAndOperatorsRecordAnAttempt() {
		var attempt = attemptInput("el", Classifier.MANUAL, YOGHURT, "h1");

		assertThrows(NotAuthenticatedException.class, () -> sut.recordAttempt(null, PRODUCT, attempt));
		assertThrows(NotAuthorizedException.class, () -> sut.recordAttempt(RETAILER_USER, PRODUCT, attempt));
		assertNothingTouched();
	}

	@Test
	void anAttemptOfTheLlmIsRefused() {
		var failure = assertThrows(BadInputValueException.class, () -> sut.recordAttempt(OPERATOR_USER, PRODUCT, attemptInput("el", Classifier.LLM, YOGHURT, "h1")));

		assertBadInput("classifier", "The attempts of the LLM are made by HowiBuy itself", failure);
		assertNothingTouched();
	}

	@Test
	void anAttemptOfAnUnknownProductIsNotFound() {
		when(mockClassificationDao.findLockedProductToClassify(any(), eq(PRODUCT))).thenReturn(Uni.createFrom().nullItem());

		var failure = recordAndExpectFailure(attemptInput("el", Classifier.MANUAL, YOGHURT, "h1"));

		assertInstanceOf(NotFoundException.class, failure);
		assertEquals("No such Retailer Product, retailerProductId: " + PRODUCT.asString(), failure.getMessage());
		assertNothingRecorded();
	}

	@Test
	void anAttemptMadeFromAnotherInputHashIsAConflict() {
		givenTheProduct("h2", Set.of("el"), "el");

		var failure = recordAndExpectFailure(attemptInput("el", Classifier.MANUAL, YOGHURT, "h1"));

		assertInstanceOf(InputHashConflictException.class, failure);
		assertEquals("The input hash is not the product's current one, product id: " + PRODUCT.asString() + ", inputHash: h1", failure.getMessage());
		assertNothingRecorded();
	}

	@Test
	void anAttemptInALanguageTheProductIsNotNamedInIsRefused() {
		givenTheProduct("h1", Set.of("el"), "el");

		var failure = recordAndExpectFailure(attemptInput("en", Classifier.MANUAL, YOGHURT, "h1"));

		assertBadInput("language", "The product is not named in it, language: en", failure);
		assertNothingRecorded();
	}

	@Test
	void anAttemptOfAnUnknownArchetypeIsNotFound() {
		givenTheProduct("h1", Set.of("el"), "el");
		var notFound = new NotFoundException(YOGHURT, "No such Archetype Product, archetypeProductId: " + YOGHURT.asString());
		when(mockArchetypeProductDao.requireProductExists(any(), eq(YOGHURT))).thenReturn(Uni.createFrom().failure(notFound));

		var failure = recordAndExpectFailure(attemptInput("el", Classifier.MANUAL, YOGHURT, "h1"));

		assertEquals(notFound, failure);
		assertNothingRecorded();
	}

	@Test
	void aMatchOfTheLlmIsRecordedAndResolvesTheClassificationWithTheProductLockedOnlyAfterTheMatching() {
		givenTheClassificationInput(Map.of("el", "Γιαούρτι"), Map.of(), List.of());
		var diagnostics = diagnostics("Dairy", "Fermented milk", "Yoghurts", "Yoghurt");
		givenTheMatchingEndsWith(new ArchetypeMatchOutcome.Matched(YOGHURT, diagnostics));
		givenTheProduct("h1", Set.of("el"), "el");
		when(mockArchetypeProductDao.requireProductExists(any(), eq(YOGHURT))).thenReturn(Uni.createFrom().voidItem());
		givenTheAttemptsAreRecorded();

		var recorded = assertInstanceOf(LlmClassification.Recorded.class, sut.classifyWithLlm(CATALOGUE_SYNC_USER, PRODUCT, "el").await().atMost(WAIT));

		assertEquals(1, mockReactivePersistenceContextFactory.getOpenedTransactions().size(), "one transaction");
		var tx = mockReactivePersistenceContextFactory.getOpenedTransactions().getFirst();
		var inOrder = inOrder(mockClassificationDao, mockArchetypeMatching, mockArchetypeProductDao);
		inOrder.verify(mockClassificationDao).findClassificationInput(any(), eq(PRODUCT));
		inOrder.verify(mockArchetypeMatching).match(any());
		inOrder.verify(mockClassificationDao).findLockedProductToClassify(tx, PRODUCT);
		inOrder.verify(mockArchetypeProductDao).requireProductExists(tx, YOGHURT);
		inOrder.verify(mockClassificationDao).recordAttempt(tx, PRODUCT, recorded.attempt());
		inOrder.verify(mockClassificationDao).findAttempts(tx, PRODUCT, "h1");
		inOrder.verify(mockClassificationDao).saveClassification(tx, PRODUCT, recorded.classification());
		inOrder.verifyNoMoreInteractions();
		var createdAt = recorded.attempt().getCreatedAt();
		assertEquals(ImmutableClassificationAttempt.builder()
				.language("el")
				.classifier(Classifier.LLM)
				.classifierVersion(LLM_VERSION)
				.archetypeProductId(YOGHURT)
				.inputHash("h1")
				.diagnostics(diagnostics)
				.createdAt(createdAt)
				.build(), recorded.attempt());
		assertEquals(ImmutableClassification.builder().archetypeProductId(YOGHURT).inputHash("h1").isNeedsReview(false).resolvedAt(createdAt).build(), recorded.classification());
	}

	@Test
	void aNoMatchOfTheLlmIsRecordedAsAnAttemptThatNoArchetypeMatches() {
		givenTheClassificationInput(Map.of("el", "Σαπούνι"), Map.of(), List.of());
		var diagnostics = diagnostics("Dairy", null, null, null);
		givenTheMatchingEndsWith(new ArchetypeMatchOutcome.NoMatch(diagnostics));
		givenTheProduct("h1", Set.of("el"), "el");
		givenTheAttemptsAreRecorded();

		var recorded = assertInstanceOf(LlmClassification.Recorded.class, sut.classifyWithLlm(OPERATOR_USER, PRODUCT, "el").await().atMost(WAIT));

		verifyNoInteractions(mockArchetypeProductDao);
		assertNull(recorded.attempt().getArchetypeProductId());
		assertEquals(diagnostics, recorded.attempt().getDiagnostics());
		assertNull(recorded.classification().getArchetypeProductId());
	}

	@Test
	void aMalformedReplyOfTheLlmIsAnsweredWithNothingRecorded() {
		givenTheClassificationInput(Map.of("el", "Γιαούρτι"), Map.of(), List.of());
		var diagnostics = diagnostics("Dairy", "Fermented milk", null, null);
		givenTheMatchingEndsWith(new ArchetypeMatchOutcome.Malformed(diagnostics, "category", "42"));

		var classification = sut.classifyWithLlm(OPERATOR_USER, PRODUCT, "el").await().atMost(WAIT);

		assertEquals(new LlmClassification.Malformed(diagnostics), classification);
		assertTrue(mockReactivePersistenceContextFactory.getOpenedTransactions().isEmpty(), "no transaction opened");
		verify(mockClassificationDao, never()).findLockedProductToClassify(any(), any());
		assertNothingRecorded();
	}

	/**
	 * The text is trimmed and its Unicode composed, as the classification input hash does, so that the products are
	 * classified from the same text whatever whitespace or composition they came with.
	 */
	@Test
	void theLlmClassifiesFromTheNameInTheLanguageAndTheCharacteristicsAndTagsAsTheyAreHashed() {
		givenTheClassificationInput(
				Map.of("el", " Γιαούρτι στραγγιστό\t", "en", "Strained yoghurt"),
				Map.of(" Λιπαρά ", " 2% "),
				List.of(" ψυγείου ", "γαλακτοκομικά"));
		givenTheMatchingEndsWith(new ArchetypeMatchOutcome.Malformed(diagnostics(null, null, null, null), "category", "42"));

		sut.classifyWithLlm(OPERATOR_USER, PRODUCT, "el").await().atMost(WAIT);

		var productCaptor = ArgumentCaptor.forClass(ProductBaseData.class);
		verify(mockArchetypeMatching).match(productCaptor.capture());
		var product = productCaptor.getValue();
		assertEquals(Locale.forLanguageTag("el"), product.getLanguage());
		assertEquals("Γιαούρτι στραγγιστό", product.getName());
		assertEquals(Map.of("Λιπαρά", "2%"), product.getCharacteristics());
		assertEquals(List.of("ψυγείου", "γαλακτοκομικά"), product.getTags());
	}

	@Test
	void onlyTheCatalogueSyncAndOperatorsClassifyWithTheLlm() {
		assertThrows(NotAuthenticatedException.class, () -> sut.classifyWithLlm(null, PRODUCT, "el"));
		assertThrows(NotAuthorizedException.class, () -> sut.classifyWithLlm(RETAILER_USER, PRODUCT, "el"));
		assertNothingTouched();
		verifyNoInteractions(mockArchetypeMatching);
	}

	@Test
	void anUnknownProductIsNotClassifiedWithTheLlm() {
		when(mockClassificationDao.findClassificationInput(any(), eq(PRODUCT))).thenReturn(Uni.createFrom().nullItem());

		var failure = classifyWithLlmAndExpectFailure("el");

		assertInstanceOf(NotFoundException.class, failure);
		assertEquals("No such Retailer Product, retailerProductId: " + PRODUCT.asString(), failure.getMessage());
		verifyNoInteractions(mockArchetypeMatching);
		assertNothingRecorded();
	}

	@Test
	void aProductNotNamedInTheLanguageIsNotClassifiedWithTheLlm() {
		givenTheClassificationInput(Map.of("el", "Γιαούρτι"), Map.of(), List.of());

		var failure = classifyWithLlmAndExpectFailure("en");

		assertBadInput("language", "The product is not named in it, language: en", failure);
		verifyNoInteractions(mockArchetypeMatching);
		assertNothingRecorded();
	}

	@Test
	void aProductThatChangedWhileTheLlmWorkedIsAConflict() {
		givenTheClassificationInput(Map.of("el", "Γιαούρτι"), Map.of(), List.of());
		givenTheMatchingEndsWith(new ArchetypeMatchOutcome.NoMatch(diagnostics("Dairy", null, null, null)));
		givenTheProduct("h2", Set.of("el"), "el");

		var failure = classifyWithLlmAndExpectFailure("el");

		assertInstanceOf(InputHashConflictException.class, failure);
		assertNothingRecorded();
	}

	@Test
	void anOperatorPreviewsTheMatchingOfTheLlmWithNothingStored() {
		var outcome = new ArchetypeMatchOutcome.Matched(YOGHURT, diagnostics("Dairy", "Fermented milk", "Yoghurts", "Yoghurt"));
		givenTheMatchingEndsWith(outcome);

		assertEquals(outcome, sut.previewWithLlm(OPERATOR_USER, "el", "Γιαούρτι", Map.of(), List.of()).await().atMost(WAIT));

		assertNothingTouched();
	}

	/**
	 * The posted text is trimmed and its Unicode composed as the stored products' is, so that a preview matches a
	 * product as its classification would.
	 */
	@Test
	void theLlmPreviewsTheMatchingOfTheProductDataAsTheStoredProductsAreClassified() {
		givenTheMatchingEndsWith(new ArchetypeMatchOutcome.NoMatch(diagnostics(null, null, null, null)));

		sut.previewWithLlm(OPERATOR_USER, "el", " Γιαούρτι\t", Map.of(" Λιπαρά ", " 2% "), List.of(" ψυγείου ")).await().atMost(WAIT);

		var productCaptor = ArgumentCaptor.forClass(ProductBaseData.class);
		verify(mockArchetypeMatching).match(productCaptor.capture());
		var product = productCaptor.getValue();
		assertEquals(Locale.forLanguageTag("el"), product.getLanguage());
		assertEquals("Γιαούρτι", product.getName());
		assertEquals(Map.of("Λιπαρά", "2%"), product.getCharacteristics());
		assertEquals(List.of("ψυγείου"), product.getTags());
	}

	@Test
	void productDataWithABlankNameIsNotPreviewed() {
		var failure = assertThrows(BadInputValueException.class, () -> sut.previewWithLlm(OPERATOR_USER, "el", " \t", Map.of(), List.of()));

		assertBadInput("name", "It must not be blank", failure);
		verifyNoInteractions(mockArchetypeMatching);
	}

	@Test
	void onlyOperatorsPreviewTheMatchingOfTheLlm() {
		assertThrows(NotAuthenticatedException.class, () -> sut.previewWithLlm(null, "el", "Γιαούρτι", Map.of(), List.of()));
		assertThrows(NotAuthorizedException.class, () -> sut.previewWithLlm(CATALOGUE_SYNC_USER, "el", "Γιαούρτι", Map.of(), List.of()));
		assertThrows(NotAuthorizedException.class, () -> sut.previewWithLlm(RETAILER_USER, "el", "Γιαούρτι", Map.of(), List.of()));
		verifyNoInteractions(mockArchetypeMatching);
	}

	private void givenTheClassificationInput(Map<String, String> names, Map<String, String> characteristics, List<String> tags) {
		when(mockClassificationDao.findClassificationInput(any(), eq(PRODUCT))).thenReturn(Uni.createFrom().item(ImmutableProductClassificationInput.builder()
				.inputHash("h1")
				.names(names)
				.characteristics(characteristics)
				.tags(tags)
				.build()));
	}

	private void givenTheMatchingEndsWith(ArchetypeMatchOutcome outcome) {
		when(mockArchetypeMatching.match(any())).thenReturn(Uni.createFrom().item(outcome));
	}

	/**
	 * The attempts found after the recording are the one recorded.
	 */
	private void givenTheAttemptsAreRecorded() {
		var recorded = new ArrayList<ClassificationAttempt>();
		when(mockClassificationDao.recordAttempt(any(), eq(PRODUCT), any())).thenAnswer(invocation -> {
			recorded.add(invocation.getArgument(2));
			return Uni.createFrom().voidItem();
		});
		when(mockClassificationDao.findAttempts(any(), eq(PRODUCT), eq("h1"))).thenAnswer(invocation -> Uni.createFrom().item(List.copyOf(recorded)));
		when(mockClassificationDao.saveClassification(any(), eq(PRODUCT), any())).thenReturn(Uni.createFrom().voidItem());
	}

	private Throwable classifyWithLlmAndExpectFailure(String language) {
		return sut.classifyWithLlm(OPERATOR_USER, PRODUCT, language)
				.subscribe().withSubscriber(UniAssertSubscriber.create())
				.awaitFailure(WAIT)
				.getFailure();
	}

	private static MatchDiagnostics diagnostics(String l1Category, String l2Category, String l3Category, String archetypeProduct) {
		return ImmutableMatchDiagnostics.builder()
				.l1Category(l1Category)
				.l2Category(l2Category)
				.l3Category(l3Category)
				.archetypeProduct(archetypeProduct)
				.build();
	}

	private void givenTheProduct(String inputHash, Set<String> languages, String classificationLanguage) {
		when(mockClassificationDao.findLockedProductToClassify(any(), eq(PRODUCT))).thenReturn(Uni.createFrom().item(ImmutableProductToClassify.builder()
				.inputHash(inputHash)
				.languages(languages)
				.classificationLanguage(classificationLanguage)
				.build()));
	}

	private Throwable recordAndExpectFailure(ClassificationAttemptInput attempt) {
		return sut.recordAttempt(OPERATOR_USER, PRODUCT, attempt)
				.subscribe().withSubscriber(UniAssertSubscriber.create())
				.awaitFailure(WAIT)
				.getFailure();
	}

	private void assertNothingTouched() {
		verifyNoInteractions(mockClassificationDao, mockArchetypeProductDao);
		assertTrue(mockReactivePersistenceContextFactory.getOpenedTransactions().isEmpty(), "no transaction opened");
	}

	private void assertNothingRecorded() {
		verify(mockClassificationDao, never()).recordAttempt(any(), any(), any());
		verify(mockClassificationDao, never()).saveClassification(any(), any(), any());
	}

	private static void assertBadInput(String inputName, String hint, Throwable failure) {
		var badInput = assertInstanceOf(BadInputValueException.class, failure);
		assertEquals(inputName, badInput.getInputName());
		assertEquals(hint, badInput.getHint());
	}

	private static ClassificationAttemptInput attemptInput(String language, Classifier classifier, ArchetypeProductId archetypeProductId, String inputHash) {
		return ImmutableClassificationAttemptInput.builder()
				.language(language)
				.classifier(classifier)
				.classifierVersion("test")
				.archetypeProductId(archetypeProductId)
				.inputHash(inputHash)
				.build();
	}

	private static ClassificationAttempt attempt(String language, Classifier classifier, ArchetypeProductId archetypeProductId, String inputHash, LocalDateTime createdAt) {
		return ImmutableClassificationAttempt.builder()
				.from(attemptInput(language, classifier, archetypeProductId, inputHash))
				.createdAt(createdAt)
				.build();
	}
}
