# Food Sustainability Scoring & Recommendation

Status: ready-for-agent

Domain vocabulary follows the project glossary (`tealhelix-architecture/src/site/markdown/CONTEXT.md`, section
"Product Sustainability Assessment"), which issue 01 revises for this work. Architecture decisions are recorded in ADR
`0005` (amended by issue 04) and a new ADR `0007` (issue 06).

Source of truth for the method: WP3's analysis notebook `paper_analysis_2026_07_31_v15.qmd`, in its default
configuration. Where WP3's answers to our questions settle something the notebook leaves open, or depart from it, this
spec says so where it applies. Input data: `TH_WP3-FULL DATABASE-v2026-10-27.csv`; substitutability matrix:
`Step1_encoding_v2026-05-28.csv`. These and every other WP3 file this spec names are in
`tealhelix-architecture/src/data/wp3/`.

## Problem Statement

The backend implements WP3's first scoring and substitution method, transcribed from an R Markdown script
(`TH_Algorithm_Implementation_v2026-05-20.Rmd`). WP3 has since replaced it. The new method differs in every stage
that matters to a user:

- **Scoring.** Plain min–max normalisation with hand-set outlier cut-offs, and a health score read off the Nutri-Score
  letter, gave way to percentile-anchored normalisation with a log transform for the skewed dimensions, and a health
  score computed from the continuous Nutri-Score points.
- **Recommendation.** Three deterministic answers (best personal, best scientific, best combined), which in practice
  mostly agree and concentrate on a handful of products, gave way to a gate ("this product is already good enough"),
  an objective floor (an alternative must beat the scanned product), and a random draw that favours what the user
  cares about.
- **Personal weights.** Social and Economic are now one Socio-economic dimension, since WP3's social data includes
  economic indicators.

Our `GOOD_ENOUGH` currently means "nothing beats the scanned product", which is the new method's "no alternative", not
its "good enough". The two must now be told apart.

## Solution

Port the new method into the existing framework-free `sustainability-scoring` engine, and carry its outcomes through
the assessment API.

**Scoring.** Every Archetype Product with a Nutri-Score is scored on four dimensions: Environment, Animal Welfare,
Socio-economic and Health. Environment, Animal Welfare and Socio-economic aggregate indicators into a Single Score as
today; Health is the product's Nutri-Score points. Each dimension is normalised against the Archetype Products that
have a Nutri-Score, anchored at the 1st and 99th percentiles and clamped to `[0, 1]`, after a log transform for Animal
Welfare and Socio-economic, and inverted so that higher means more sustainable. Products without a Nutri-Score are left
out of the corpus altogether.

**Recommendation.** For the Reference Product:

1. **No score:** it has no Nutri-Score, so nothing is said about it.
2. **Gate:** its scientific Overall Score is at least 0.80. It is good enough; no Alternative is sought.
3. **Pool:** the stocked Archetype Products of every category substitutable for its own at degree 4 or 5, whose
   scientific Overall Score is strictly higher than its own. An empty pool means no Alternative exists.
4. **Draw:** one Archetype Product from the pool, at random, with probability proportional to
   `exp(15 × personal Overall Score)`. The random source is seeded from the user and the Reference Product, so the same
   user scanning the same product sees the same Alternative.

The draw is the only place the user's weights act. The gate and the pool use the scientific profile alone, so no
weighting a user can express can lead to an objectively worse product.

**Two steps.** Step 1 (issues 01–07) delivers the method with one Alternative, at WP3's level 4 (matrix degrees 4–5).
Step 2 (issue 08) offers up to three, one per "ring" of the substitutability matrix (degree 5, 4 and 3 each on its
own). The API carries a list of Alternatives from step 1, so the Retailers adapt once.

## User Stories

1. As a user scanning a product, I want to be told a more sustainable alternative, so I can make a better choice at the shelf.
2. As a user, I want the alternative to lean towards what I said matters to me in the compass, so the advice is mine and not generic.
3. As a user, I want to be told when the product I scanned is already a good choice, rather than being pushed a substitute I don't need.
4. As a user, I want to be told when nothing better is available, as a different answer from "already a good choice".
5. As a user, I want an alternative that is a plausible replacement for what I'm buying, not an arbitrary healthier food.
6. As a user, I want to be told how much better the alternative is, so I can judge whether the swap is worth it.
7. As a user, I want a recommendation that never steers me toward something objectively worse, even where my own priorities would rank it highly.
8. As a user, I want to see the same alternative when I scan the same product again, so the app does not look erratic.
9. As a user who has not completed the compass, I want to still get a scientifically-grounded recommendation.
10. As a user scanning a product we cannot score, I want to be told plainly that no assessment is available.
11. As the project, I want the backend's scores to be verifiable against WP3's R reference, so we can show the port is faithful.
12. As a Retailer, I want the response shape to stay the same when HowiBuy starts offering several alternatives, so I integrate once.

## Implementation Decisions

**Method parameters** (v15 defaults, confirmed by WP3)
- Normalisation profile `log_tails`: Environment and Health percentile min–max; Animal Welfare and Socio-economic
  `log(max(x, 0) + m)` first, where `m` is the median of the dimension's positive Single Scores, then percentile
  min–max. Percentiles 0.01 and 0.99, R's default quantile (type 7, linear interpolation).
- Gate: scientific Overall Score `>= 0.80`. Pool: scientific Overall Score `>` the Reference Product's, the Reference
  Product itself excluded. Substitutability: degree `>= 4` (step 1). Softmax sharpness 15.
- The scientific profile weights the four dimensions equally (0.25).
- The indicator weights and PEF normalisation factors are unchanged from the current transcription; v15 uses the same
  values.
- All of these are constants in the engine, each commented with its origin in the notebook. None is Quarkus
  configuration: they are one verified set, and WP3 changes them, not operators.

**The corpus that defines the scale.** The normaliser scores and normalises exactly the products it is given. The
engine's public entry point passes the Archetype Products that have a Nutri-Score; the products marked `"0"` (47 in
10-27: infant food, three baking ingredients and four foods for weight reduction) are left out of the anchors, the log
offsets, the gate and the pool, as WP3 decided. The parity test (below) passes all 2,451, because WP3's published
scores were computed that way.

**Data**
- `TH_ARCHETYPE_PRODUCT` gains `nutri_score_points INTEGER NOT NULL`, WP3's `H_FNS_score`: the points behind the
  Nutri-Score letter, lower being healthier. Products without a Nutri-Score carry WP3's placeholder 0; they are
  recognised by `nutri_score = '0'`, never by the points.
- The seed is regenerated from `TH_WP3-FULL DATABASE-v2026-10-27.csv`. Against the current seed (05-27), it carries
  values at full precision, 40 changed Nutri-Score values (the alcoholic drinks, `"0"` in 05-27, are E in 10-27), 3
  changed Animal Welfare values and the water fix. Archetype ids are UUIDv5 of the AGB code and the 2,451 AGB codes
  are unchanged, so every id, Classification and Purchased Basket survives.
- **Existing changesets are never edited**: they have all run in a dev environment. One new changelog,
  `20261105_sustainability_scoring.xml`, holds every change of this work:
  - `addColumn` of `nutri_score_points`, nullable;
  - `loadUpdateData` (context `appdata`, primary key `id`) of a new CSV, `archetype_product_v2026-10-27.csv`, holding
    every column of every product. Its rollback reloads the original `archetype_product.csv` the same way;
  - `addNotNullConstraint` on `nutri_score_points`, preconditioned on the update having run: a database migrated
    first without `appdata` and then with it loads the original products, which have no points, after every changeset
    without a context;
  - `renameColumn` of `TH_PURCHASED_BASKET_PRODUCT.score_social` to `score_socio_economic`.
- `generate-archetype-data.js` gets the 10-27 input, the new column and an option naming the product file it writes,
  so the original `archetype_product.csv` stays byte-identical. The category CSV it regenerates must not change; if
  it does, the script's output is wrong for this changelog and the work stops.
- Future WP3 data versions follow the same pattern: a new product CSV named after the WP3 version, and a new
  `loadUpdateData` changeset.

**Engine** (`sustainability-scoring`)
- `ScoredSustainabilityDimension.SOCIAL` becomes `SOCIO_ECONOMIC`, everywhere it appears, including the column of the
  Purchased Basket.
- `WeightProfile` carries the four dimension weights only. The indicator weights become engine constants, so there is
  exactly one scored corpus, and `CorpusScoring`'s memo keyed on indicator weights goes.
- `MinMaxNormalisation` (with its outlier cut-offs 4 and 100) and the letter map of `HealthScore` go; percentile
  normalisation and the points-based health score replace them.
- `SubstitutionSearch`'s three criteria, `SubstitutionSettings` (the 0.6/0.4 blend, the SMALL/MEDIUM/LARGE cut-offs)
  and `SubstitutabilityLevel` go. The search returns a recommendation: one of the four outcomes, the Reference
  Product's scientific Overall Score when it has one, and a list of Alternatives (at most one in step 1).
- The draw takes a `java.util.random.RandomGenerator` as a parameter; the engine never creates one. Candidates are
  ordered by AGB code before the draw, so a seed always picks the same product from the same pool. A pool of one is
  returned without drawing.
- No argmax ("deterministic") mode: parity on the picks was dropped, so nothing would use it. It comes back if WP3
  asks for pick-by-pick output.

**Seeding** (`howibuy-services`)
- The seed is derived from the internal `UserId` and the Reference Product's `ArchetypeProductId`, by a hash fixed by
  specification (e.g. `UUID.nameUUIDFromBytes` over both ids), and fed to `java.util.Random`, whose algorithm the JDK
  specifies. Nothing identity- or run-dependent goes in, so the answer survives restarts and redeploys.
- Every assessment has a person (`requirePerson`); the Retailer's purchase path impersonates one first. So there is no
  "no user" case to cover.
- The answer changes, correctly, when the user retakes the compass, when the Catalogue stocks something new, or when
  the data or matrix change.

**Personal weights from the compass** (`PersonalWeightsProvider`)
- Per compass Sustainability Dimension, the mean of its 1–5 answers, mapped to 0–4 (`NOT_IMPORTANT` means weight 0).
  The notebook takes the weights from 100 points a respondent splits across the five dimensions, a question our
  compass does not ask; WP3 agreed to this mapping in its place.
- Socio-economic is the mean of the Social and Economic values, as in the notebook. Ecological maps to Environment.
- The four values normalised to sum to 1; all zero gives equal weights.
- No completed attempt, or a completed attempt missing any of the five dimensions, gives the scientific profile, as
  today.

**API** (version 0.2.0 → 0.3.0; Retailers are told before the release)
- `ProductAssessmentOutcome` loses `bestPersonalAlternative`, `bestScientificAlternative` and
  `bestCombinedAlternative`, and gains, for `SUCCESS`:
  - `recommendation`: `RECOMMENDED | GOOD_ENOUGH | NO_ALTERNATIVE | NO_SCORE`;
  - `referenceOverallScore`: the Reference Product's scientific Overall Score, absent for `NO_SCORE`;
  - `alternatives`: a list, non-empty exactly when `RECOMMENDED`.
- `AlternativeForProduct` keeps `archetype` and `product`, loses `type` and `referenceOverallScore`, and renames
  `alternativeOverallScore` to `overallScore`, also scientific. Only the scientific score is guaranteed to improve, so
  only it can say how much better an Alternative is: the personal Overall Score of a draw may be below the Reference
  Product's.
- `AlternativeForProductType` goes; the new enum is `RecommendationType` in `eu.tealhelix.howibuy.v1.types`.
- The list is named `alternatives`, not `suggestions`, after the glossary term.
- `openapi.yaml` describes the new fields; `OpenApiContractTest` keeps passing.

**Basket assessment** is unchanged in behaviour. Its Product Grades and stored scores move with the new normalisation,
and its tests follow the new numbers.

## Step 2: rings (issue 08, after step 1 is released)

Up to three Alternatives, one per ring of the matrix: degree 5 ("closest match"), degree 4, degree 3, each ring drawn
on its own from its own pool, in that order, with the same seeded random source. The rings are disjoint (a category has
one degree against the Reference Product's), so no exclusion between draws is needed. An empty ring is skipped. WP3
measured "level 4 is the best balance" on nested pools only and may test rings; step 2 waits for that if it comes.

## Testing Decisions

1. **Engine unit tests** (plain JUnit + AssertJ). Hand-computed fixtures: the type-7 quantile, including interpolation
   between two values; the log offset over positive values only; clamping at both ends; the inversion; the scale
   defined by the given products only; the gate at exactly 0.80; the strict pool (equal score excluded, self excluded);
   the Substitutability Level; the stocked filter; the four outcomes; the draw with a stubbed `RandomGenerator`
   choosing each candidate; the draw's distribution with a seeded generator over many draws, against the softmax
   probabilities; determinism of the order before the draw.
2. **Parity with WP3.** All 2,451 products of 10-27 scored by the engine against the `log_tails` rows of WP3's
   `CORE_scores_by_profile.csv`, within `1e-9` on E, AW, SE, H and overall, and identical gate outcomes. The fixture is
   one CSV in the engine's test resources: AGB code, Nutri-Score, Nutri-Score points, the 32 indicators under their
   `SustainabilityIndicator` names, and the expected five scores. A script in `tealhelix-architecture/src/scripts/`
   generates it from the two WP3 files in `tealhelix-architecture/src/data/wp3/`.
3. **DAO tests** (Testcontainers, context `test`): the new column maps.
4. **Data integrity** (`ArchetypeDataIntegrityTest`, context `appdata`): after the update, every product has its 10-27
   values (sampled, including the two water rows), and the counts are unchanged.
5. **Service tests** (Mockito + Weld): the outcome mapping into the API model; the seed (same user and product, same
   pick; another user, possibly another pick); the personal weights mapping; Alternatives filled with a Retailer
   Product only when `RECOMMENDED`.
6. **Workflow tests** (`ProductAssessmentTest`, `BasketAssessmentTest`, the e2e `ProductAssessmentIT`): updated to the
   new response shape and the new numbers.

## Out of Scope

- Rings and several Alternatives (step 2, issue 08).
- Gamification: XP, streaks, badges, the basket thresholds against the new scale. It follows this work.
- Scoring products without a Nutri-Score on their other dimensions.
- Exposing dimension scores through the product assessment API.
- Persisting what was recommended to whom.
- The frontend and the Retailers' integration.

## Further Notes

- Respect ADR 0005 and `CodingConventions.md` throughout. Test-first.
- The four scored dimensions and the compass's five Sustainability Dimensions stay separate types, mapped explicitly
  in one place (`PersonalWeightsProvider`).
- WP3's column prefixes (`E_`, `AW_`, `S_`, `H_`, `_SS`) are research notation and stay out of Java names.
- After the last issue, rerun the overall-score distribution measurement of the basket thresholds
  (see the gamification notes) before anyone recalibrates them.
