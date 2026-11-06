# Basket Gamification

Status: ready-for-agent

Domain vocabulary follows the project glossary (`tealhelix-architecture/src/site/markdown/CONTEXT.md`, sections
"Product Sustainability Assessment" and "Basket Assessment"). The rules come from WP5.3's spreadsheet "TH WP5.3
GAMIFICATION FORMULAS.xlsx" (outside the repository). Its OSS is our scientific Overall Score, its PSS our personal
Overall Score; neither abbreviation is used in code or in the API.

## Problem Statement

The Basket Assessment (`../004-basket-assessment/spec.md`) was built with stub rules: a Product Grade per product, the
worst of them as the Basket Grade, and 3/1/0 XP per grade. WP5.3 has now specified the real rules: XP for sustainable
products and for a sustainable basket, judged on the scientific Overall Score; a match of the basket with the user's
own priorities, judged on the personal Overall Score; a streak of purchases with a sustainable product; and badges for
the products a user has bought that excel in one dimension.

## Solution

`BasketRules` is rewritten to the real rules. The Basket Grade gives way to the **Basket Match**, and the Product Grade
disappears. Each scored product of a basket carries both its Overall Scores, and both are stored. The user profile
gains a **Streak**, and the user earns **Badges**, which are stored once earned. Both basket endpoints report the
basket's effect on all of it, as they already do for XP: before the purchase what the basket would do, after it what it
did. The API moves to version 0.4.0.

The read side (a dashboard endpoint listing the badges with the progress to the next one, the monthly progress, the XP
percentile) is left to the howibuy-front dashboard work, its primary client.

## User Stories

1. As a retailer's customer, I want to see how well my basket matches my own priorities (excellent, good, needs
   improvement) before I check out, so I can improve it while I still can.
2. As a retailer's customer, I want XP for every sustainable product I buy and a bonus for a sustainable basket as a
   whole, judged by the same science for everyone, so that the reward means the same for every user.
3. As a retailer's customer, I want to see which products keep my basket from being sustainable, worst first.
4. As a retailer's customer, I want a streak of purchases that each contain a sustainable product, and to know before I
   buy whether a basket extends or breaks it.
5. As a retailer's customer, I want to earn badges for the products I buy that excel in one dimension, and to be told
   when a basket earns one, or would.
6. As a retailer, I want a code and a translated label for everything, so I can decide what to show and draw my own
   icons for the badges.
7. As a business person, I want the match labels and the badge names in every pilot language, translated by the
   partners, without a code change beyond a bundle file.
8. As a researcher, I want both Overall Scores of every purchased product stored, for statistics.
9. As the project, I want the badges users have earned to survive a recalibration of the thresholds.

## Implementation Decisions

### API (version 0.4.0)

The endpoints, the requests and the tokens of `../004-basket-assessment/spec.md` stay as they are. Only the outcome
changes. `JaxRsAppHowiBuyV1` declares version 0.4.0; the change is breaking (fields removed).

`BasketAssessmentOutcome` (`howibuy-model`):

- **Removed:** `getBasketGrade()`, `getBasketGradeLabel()`.
- **Added:**
  - `getBasketMatch()`, `getBasketMatchLabel()`: `@Nullable`, set only for `ASSESSED`.
  - `getStreak()`: like `getTotalXp()`, the user's current Streak before the purchase, and including this basket once
    purchased.
  - `getStreakEffect()`: like `getXpValue()`, the basket's effect on the Streak, `NONE` for `NOTHING_TO_ASSESS`.
  - `getEarnedBadges()`: like `getXpValue()`, the Badges this basket earns: would earn before the purchase, has earned
    after it. Each is an `EarnedBadge { badge, label }`. Empty when none, and for `NOTHING_TO_ASSESS`.
- **Redefined:** `getXpValue()` and `getProblematicProductKeys()` follow the real rules below.
- `getType()`: `ASSESSED` when at least one product has an Overall Score, as before.
- `language` picks the Basket Match label and the badge labels.

New types in `howibuy-model`:

- `v1.types.BasketMatch { EXCELLENT, GOOD, NEEDS_IMPROVEMENT }`, replacing `BasketGrade`.
- `v1.types.StreakEffect { EXTEND, RESET, NONE }`.
- `v1.types.Badge`, the twelve badge codes (see Badges). Its Javadoc says what a badge is, not what awards it.
- `v1.model.EarnedBadge`, an Immutables interface: `getBadge()`, `getLabel()`.

`v1.types.ProductGrade` and `v1.types.BasketGrade` are deleted.

### Scoring

Each scored product of a basket carries two Overall Scores:

- the **scientific** one, under `ScientificWeights.profile()`, which decides everything except the match;
- the **personal** one, under the Weighting Profile in force for the user, as today: the personal profile, or the
  scientific one for a user without a completed compass Attempt. It decides the Basket Match only.

What counts as a scored product is unchanged.

### Rules

`BasketRules` (`howibuy-services`, concrete, framework-free, hard-coded) is rewritten. Every comparison is a strict `>`.

- **Input:** the scored products, each once: `ScoredBasketProduct(productKey, scientificOverallScore,
  personalOverallScore)`.
- **XP:** 10 for every product whose scientific Overall Score is above 0.8, plus a **basket bonus** when the basket has at
  least `BONUS_MIN_PRODUCTS` (5) scored products: 150 if their average scientific Overall Score is above 0.8, otherwise
  50 if it is above 0.6, otherwise none. The bonuses exclude each other. The spreadsheet's example is a basket of ten
  products, four of them above 0.8, averaging 0.7096: 40 + 50 = 90 XP. Its ten scores are 0.979980707166845,
  0.273794244601, 0.749770266076726, 0.909381114899094, 0.62459833995715, 0.658515573444208, 0.771485302265451, 0.99,
  0.148262799383649, 0.99.
- **Basket Match:** the average personal Overall Score above 0.8 is `EXCELLENT`, above 0.6 `GOOD`, otherwise
  `NEEDS_IMPROVEMENT`. `null` when no product is scored.
- **Problematic Products:** the scored products whose scientific Overall Score is 0.8 or less, lowest first, uncapped.
- **Streak effect** (issue 04): `EXTEND` when some product's scientific Overall Score is above 0.8, `RESET` when products
  are scored but none is, `NONE` when no product is scored.
- **Output:** `BasketVerdict(@Nullable basketMatch, xpValue, problematicProductKeys, streakEffect)`.

"Above 0.8 on the scientific Overall Score" is one notion shared by the product XP, the Problematic Products and the
streak, so it is one constant. The average thresholds of the bonus and of the match are constants of their own, even
where the values coincide: WP5.3 may recalibrate them separately.

### Streak

- `TH_USER_PROFILE.STREAK INT NOT NULL DEFAULT 0`, next to `XP_TOTAL`, not exposed through `UserProfile`.
- In the purchase transaction, with an atomic `CriteriaUpdate` like `addXp`: `EXTEND` sets `STREAK = STREAK + 1`,
  `RESET` sets it to 0, `NONE` leaves it.
- Purchases count in the order they are registered, not by `purchasedAt`.
- The per-basket effect is not stored.

### Badges

A **Badge** is identified by a code, the `Badge` enum constant's name, which is stored and is the key of its label.
Each level of a series is a badge of its own. What awards a badge is a rule in `howibuy-services`, not part of the
badge's identity.

The twelve badges, each awarded when the user has bought a number of products whose Normalised Score of one dimension is
above 0.8:

| Badges                         | Dimension      | Products     | English label (placeholder except Environment) |
|--------------------------------|----------------|--------------|-------------------------------------------------|
| `ENVIRONMENTAL_HERO_1`..`_3`   | Environment    | 50, 100, 200 | Environmental Hero Level 1..3                   |
| `ANIMAL_WELFARE_HERO_1`..`_3`  | Animal Welfare | 50, 100, 200 | Animal Welfare Hero Level 1..3                  |
| `SOCIO_ECONOMIC_HERO_1`..`_3`  | Socio-economic | 50, 100, 200 | Socio-economic Hero Level 1..3                  |
| `HEALTH_HERO_1`..`_3`          | Health         | 50, 100, 200 | Health Hero Level 1..3                          |

- **Counting:** every product of every Purchased Basket of the user counts once per basket. Quantities are not modelled,
  so ten of one yoghurt in a basket count 1, the same yoghurt in the next basket 1 more, and ten different yoghurts in one
  basket 10. Only scored products are stored, so only they count.
- **Earning:** a basket earns the badges whose count, the user's stored products plus this basket's, reaches the
  badge's number, and which the user does not hold yet. A basket may earn several, reported in the enum's order.
- **One rule for both endpoints:** both compute the earned badges before anything is stored. The purchase then stores
  them with the basket.
- **Stored badges are kept.** A raised number takes no badge away. A lowered one awards the newly reached badges with the
  user's next basket, credited to that basket.
- `BadgeRules` (`howibuy-services`, framework-free, pure): from the stored count per dimension, the badges held and the
  Normalised Scores of the basket's products, the badges earned.
- The codes may be renamed freely until a real user earns a badge; after that, a rename needs a data migration.

### Concurrency

Earning a badge reads and then writes, so the purchase serialises one user's registrations: its transaction first locks
the user's profile row, with a new `UserProfileDao` method using `tx.find(UserProfileEntity.class, id,
PESSIMISTIC_WRITE)`, as `ClassificationDaoImpl` does. A second purchase of the same user waits for the first to commit
and then sees its basket and badges; other users never wait. The pre-purchase assessment stores nothing and does not
lock.

### Labels

`BasketGradeLabels` is generalised into `Labels<E extends Enum<E>>` (issue 02), with `labelOf(E, language)` and the
same rules as today: `.properties` bundles keyed by the constant's name, English in the root bundle, no fall-back to the
server's locale, a WARN for a key a translation lacks. Two bundles in `howibuy-services`:

- `BasketMatchLabels`: `EXCELLENT=Excellent match`, `GOOD=Good match`, `NEEDS_IMPROVEMENT=Needs improvement` (the
  spreadsheet's wording).
- `BadgeLabels`: the twelve labels of the table above.

The partners supply the translations; a developer commits them as `_xx.properties`.

### Persistence

New changelog `changelogs/v1.0.0/20261105_basket_gamification.xml` in `howibuy-dao-hibernate-reactive`, included by
`howibuy.db.changelog.xml`. Every issue adds its changesets to it; existing changesets are never edited.

- `TH_PURCHASED_BASKET_PRODUCT` (issue 03): `overall_score` renamed to `personal_overall_score`; new
  `scientific_overall_score DOUBLE PRECISION NOT NULL`, stored although derivable, as the record of what decided the XP;
  `product_grade` dropped.
- `TH_PURCHASED_BASKET` (issue 03): `basket_grade` dropped; new `basket_match VARCHAR(20)`, nullable.
- `TH_USER_PROFILE` (issue 04): new `streak`, see Streak.
- `TH_USER_BADGE` (issue 05): `user_profile_id` (FK to `TH_USER_PROFILE`), `badge VARCHAR(50)`, `purchased_basket_id`
  (FK to `TH_PURCHASED_BASKET`, the basket that earned it, which also tells when); primary key (`user_profile_id`,
  `badge`). Stored by `PurchasedBasketDao.store` with the basket, in the same `persistAll`; `PurchasedBasket` gains the
  earned badges.
- Two reads for the badges, on `PurchasedBasketDao`: the number of the user's stored products above a Normalised Score,
  per dimension, in one query; and the badges the user holds.
- Not stored: the basket averages, the streak effect.

**Existing baskets are not migrated.** Before deploying issue 03, the baskets of every database are deleted by hand
(both basket tables) and `XP_TOTAL` reset to 0. Only our own test calls are stored anywhere: the purchase registration
still waits on Rimi's authentication. The new `NOT NULL` column without a default is the guard: the migration fails
loudly on a database that still holds baskets.

## Testing Decisions

- `BasketRules`: plain JUnit, table-style. XP per product and both bonuses; the boundaries 0.6 and 0.8 (exactly 0.8 is
  not above); 4 and 5 scored products for the bonus; the spreadsheet's 90-XP example; a match that differs from what the
  scientific scores would say; the problematic order; the three streak effects; the empty input.
- `BadgeRules`: plain JUnit, table-style. A count reaching a number exactly; a basket crossing two levels at once; a
  badge already held; a held badge whose count is below its number (after a raise); several dimensions at once.
- `Labels`: the shared behaviour tested once, against the generic class (translation, fall-back with a WARN, silent
  fall-back for an unsupported language); "every constant has an English label" once per bundle, so a new `Badge`
  without a label fails the build.
- DAO tests against the real DB: the products stored and read back with both scores; the streak increment and reset;
  the counts per dimension (only the user's own baskets, strictly above the score); the badges stored with the basket
  and read back; the lock of a missing profile is a `NotFoundException`.
- Endpoints (`BasketAssessmentTest`, `@QuarkusTest`): the new fields of both outcomes; a streak over successive purchases
  (`EXTEND`, `RESET`, `NONE`); a badge reached at its number, reported by the pre-purchase assessment and by the
  purchase, and not again by the next basket (the stored products below the number are seeded in the DB).
- `OpenApiContractTest`: version 0.4.0, the outcome's keys, the new enums, `EarnedBadge`.
- The lock is not tested for concurrency; that would need two overlapping transactions on one user.

## Out of Scope

- The read side, left to the dashboard work: the read endpoint, the badge listing with the progress to the next badge,
  the monthly progress per dimension and its time zone, the XP percentile.
- A sliding weekly or monthly average of the scores, and the display scale (0–1, %, 0–20): question marks in the
  spreadsheet.
- Leaderboards.
- The intervention message (still `null`).
- Recomputing stored baskets when the rules or the scores change.
- Idempotency of the purchase registration, quantities of the basket lines, configurable thresholds.

## Further Notes

Questions for the spreadsheet's authors, to be asked before the pilot:

- **The thresholds against the v15 scores.** Measured on the archetype corpus (unweighted by what people buy, see
  `helperscripts/OssDistribution.java`): 42.7% of the archetypes are above 0.8 on the scientific Overall Score and 89.4%
  above 0.6, so the 50-XP bonus is close to automatic. Per dimension above 0.8: Environment 80.4%, Animal Welfare 68.0%,
  Socio-economic 75.9%, Health 23.7%.
- **The badge speed.** With a weekly basket of 30 scored products, Level 3 of Environment, Socio-economic and Animal
  Welfare takes about 9–10 weeks, Health about 28.
- **The bonus minimum**, 5 scored products, a placeholder of ours.
- **The names of the nine placeholder badges.**

The Gate (`>= 0.80`, fixed by WP3) and the gamification's "above 0.8" differ only at exactly 0.8.
