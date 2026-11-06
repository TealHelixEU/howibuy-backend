# Basket Assessment

Status: ready-for-agent

The rules and the outcome below are superseded by `../005-basket-gamification/spec.md` (API 0.4.0): the Basket Match
replaces the Basket Grade, the Product Grade is gone, and the XP follows WP5.3's rules.

Domain vocabulary follows the project glossary (`tealhelix-architecture/src/site/markdown/CONTEXT.md`, sections
"Product Sustainability Assessment" and "Basket Assessment").

## Problem Statement

As a retailer's customer, I want one verdict on my whole basket, not one per product, and I want to be rewarded when I
actually buy sustainably. Today HowiBuy only assesses products one by one.

## Solution

HowiBuy assesses a Basket as a whole: each product is identified and scored as in the product assessment, the scored
products are graded, and rules turn the Product Grades into one Basket Grade and an XP value. Before the purchase, the
assessment is answered and nothing is kept. After the purchase, the retailer's backend registers the Purchased Basket:
it is stored with the scores of its products, and its XP is added to the user's total.

The rules for the Basket Grade and the XP are not specified yet. This spec builds everything around them with stub
rules, kept in one class that is edited when the real rules arrive.

## User Stories

1. As a retailer's customer, I want my basket graded A, B or C before I check out, so I can improve it while I still can.
2. As a retailer's customer, I want to see which products drag my basket down, worst first.
3. As a retailer's customer, I want to earn XP for the sustainable products I buy, and see my total.
4. As a retailer's customer, I want products HowiBuy cannot judge (non-food, unknown, unclassified) to be left out, not to count against me.
5. As a retailer, I want to register a purchase from my backend, so that only real purchases earn XP.
6. As a retailer, I want to state when a purchase happened, so that registering it late does not move it to another day.
7. As a retailer, I want HowiBuy to send the full information and to decide myself how much of it to show.
8. As a researcher, I want every purchased basket stored with the grade, XP, and the overall and per-dimension scores of each product, for statistics.
9. As an operator, I want to know which Weighting Profile a stored score was computed with, to verify a result.

## Implementation Decisions

### API (version 0.2.0)

The product assessment lives under `assessment/product` (done). The basket assessment adds, in `howibuy-jaxrs`, package
`eu.tealhelix.howibuy.jaxrs.v1.assessment`:

```
POST /api/howibuy/v1/assessment/basket
  Token: impersonation token (user + Catalogue), as the product assessment
  Body:  BasketAssessmentRequest { language, productKeys }
  → BasketAssessmentOutcome; nothing stored; totalXp = the user's current total

POST /api/howibuy/v1/assessment/basket/purchase
  Token: the Retailer's own service token (RETAILER role)
  Body:  PurchasedBasketAssessmentRequest { correlationId, catalogueId, purchasedAt?, language, productKeys }
  → BasketAssessmentOutcome; stored; xpValue added to the user's total, which totalXp includes
```

- The two request records are flat; the second repeats `language` and `productKeys`.
- The purchase resolves the user with `UserImpersonationService.impersonateUserAsRetailer(retailer, correlationId,
  catalogueId)`, which checks the Retailer, its Catalogue and the user's consent, and brings an unseen user into being.
  The resource then calls the same `BasketAssessmentService.assessPurchasedBasket(user, catalogueId, ...)` an
  impersonation token would; switching the endpoint to the impersonation token touches the resource only. Whether
  Rimi accepts this is an open question to Rimi.
- At most `ProductAssessmentResource.MAX_PRODUCTS` (400) keys, counted as sent; more is `400`.
- Repeated keys are merged silently. Quantities are not modelled (a known limitation): each distinct product counts once.
- An empty list is not an error: the outcome is `NOTHING_TO_ASSESS` (and, for a purchase, it is stored).
- `purchasedAt` is optional, ISO-8601 with an offset, and defaults to the time of the request. It may not be in the future.
- **No idempotency.** The caller must register a purchase exactly once; a repeated call stores the basket again and
  adds its XP again. The Javadoc and the OpenAPI description of the purchase endpoint say so.
- `language` picks the Basket Grade label. An unsupported language falls back to English.

### Outcome

`BasketAssessmentOutcome` (`howibuy-model`):

- `getType()`: new enum `BasketAssessmentOutcomeType { ASSESSED, NOTHING_TO_ASSESS }`. `NOTHING_TO_ASSESS` when no
  product of the basket has an Overall Score.
- `getBasketGrade()`, `getBasketGradeLabel()`: `@Nullable`, set only for `ASSESSED`.
- `getIntervention()`: always `null` for now.
- `getProblematicProductKeys()`: every scored product graded below A, lowest Overall Score first, uncapped. Empty for
  `NOTHING_TO_ASSESS`.
- `getXpValue()`: 0 for `NOTHING_TO_ASSESS`.
- `getTotalXp()`: the user's total; for a Purchased Basket it includes this basket's XP.
- Nothing is answered per product.

### Scoring

- The Weighting Profile in force is the personal one if the user has completed the compass, otherwise the scientific
  one: `PersonalWeightsProvider.forUser`. It is extended to also report the id of the compass Attempt it derived the
  weights from, `null` when it used the scientific profile. That includes the case where an Attempt exists but
  `derive()` falls back to the scientific profile.
- A product's score is the Overall Score of its archetype under the profile in force.
- Reused as is: `ReferenceProductIdentifier.identify(catalogueId, keys)`, `ArchetypeCorpus`. Not used: the substitution
  search, the stocked archetypes, the listed-product names.
- `ScoredArchetypes` gains `find(ArchetypeProductId) → Optional<ScoredProduct>`, delegating to `ScoredCorpus.find`.
- A product is unscored, and does not count, when it is unknown, not classified, matches no archetype, or its archetype
  is not in the scored corpus (no Nutri-Score).
- No per-product failure isolation: an exception fails the whole request (`500`) and nothing is stored.

### Rules

One concrete, framework-free class `BasketRules` in `howibuy-services`, everything hard-coded:

- New enum `ProductGrade { A, B, C }`: A for an Overall Score ≥ 0.8, B for ≥ 0.6, otherwise C. The cut-offs are
  constants next to it.
- `BasketGrade` (existing draft) is a separate enum. Stub rule: the worst Product Grade of the basket.
- XP stub: 3 per A product, 1 per B, 0 per C.
- Input: the scored products (product key, Overall Score). Output: the Product Grade of each, the Basket Grade, the XP
  value, the problematic product keys.
- Basket Grade labels: a `java.util.ResourceBundle` in `howibuy-services` (UTF-8 `.properties`), English as the
  fallback for a missing translation and for an unsupported language (with a WARN log for a missing translation).
  The business people supply the wording; a developer commits it.

### Persistence

New changelog `changelogs/v1.0.0/20261031_basket_assessment.xml` in `howibuy-dao-hibernate-reactive`, included by
`howibuy.db.changelog.xml`. `TIMESTAMP` / `LocalDateTime`, as everywhere else.

- `TH_USER_PROFILE.XP_TOTAL INT NOT NULL DEFAULT 0`. Not exposed through `UserProfile` / `UserProfileData`. Incremented
  with an atomic `UPDATE ... SET XP_TOTAL = XP_TOTAL + :xp` (`CriteriaUpdate`), in the same transaction that stores the
  basket.
- `TH_PURCHASED_BASKET`: id, user id (FK to `TH_USER_PROFILE`), retailer id, catalogue id, `purchased_at`,
  `recorded_at`, outcome type, basket grade (nullable), XP value, `compass_attempt_id` (nullable, no FK: the SFC
  tables are another bounded context).
- `TH_PURCHASED_BASKET_PRODUCT`, one row per scored product only: basket id (FK), product key, archetype product id
  (no FK), Overall Score, Product Grade, and the four normalised scores as `DOUBLE PRECISION NOT NULL` columns
  (`score_environment`, `score_animal_welfare`, `score_socio_economic`, `score_health`). No FK to
  `TH_RETAILER_PRODUCT`: the Catalogue Sync deletes its rows.
- A `NOTHING_TO_ASSESS` purchase is stored with a null grade, XP 0 and no product rows.

## Testing Decisions

- `BasketRules`: plain JUnit, table-style (scores → grades, Basket Grade, XP, problematic order), including the
  boundaries 0.6 and 0.8 and the empty input.
- Labels: every grade in every bundle; fallback for a missing translation and for an unsupported language.
- `PersonalWeightsProvider`: the attempt id is reported for a derived profile, and `null` for the scientific fallback,
  including the fallback with an existing Attempt.
- Endpoints: `@QuarkusTest` with the real DB: a mixed basket (scored, unknown, unclassified, no archetype, no
  Nutri-Score), duplicates, empty list, more than 400 keys, wrong token kinds (`403`).
- Purchase: the stored rows read back (basket, products, dimension scores, attempt id), `XP_TOTAL` incremented, two
  purchases add up, a `NOTHING_TO_ASSESS` purchase stored, `purchasedAt` default and future rejection.
- `OpenApiContractTest` updated for the new paths.

## Out of Scope

- The real Basket Grade and XP rules (they replace the stubs in `BasketRules`).
- The intervention message.
- Quantities, weights or prices of the basket lines.
- Idempotency of the purchase registration.
- Streaks and any other statistics beyond `XP_TOTAL`.
- Configurable thresholds.
- Editing of the labels by business people without a release.
