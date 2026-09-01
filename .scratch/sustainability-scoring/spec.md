# Food Sustainability Scoring & Substitution

Status: ready-for-agent (with one flagged unknown — see *Provisional & Unresolved*)

Domain vocabulary follows the project glossary (`tealhelix-architecture/src/site/markdown/CONTEXT.md`),
which this work extends. Architecture decisions are recorded in ADR `0005`; this spec must be
implemented consistently with it, and with the existing SFC ADRs `0001`–`0004` where they touch the
compass.

Source of truth for the method: `R-algorithm/R version algorithm/` — the `.Rmd`, its `README.md`,
and `CLAUDE.md`. Open methodology questions are tracked in `questions-ku-leuven.md`.

## Problem Statement

The product assessment pipeline already identifies *which* archetype a scanned product corresponds
to — the AI-driven descent through the SAFAD taxonomy in `SingleProductAssessor` works today. But
the step that matters to the user is stubbed: `makeDummyProductAssessmentOutcome` returns the
hardcoded string `"The best personal alternative"` for every product.

WP3 (KU Leuven) has delivered the real method as an R Markdown research script: it scores every food
product across four sustainability dimensions under two weighting profiles, then recommends a more
sustainable substitute. It is a batch script over a fixed CSV corpus, with hardcoded weights and no
notion of a user. We need it as a service: per user, per scanned product, on request.

## Solution

Port the WP3 scoring and substitution algorithm into the backend as a framework-free engine, and
wire it into the existing assessment flow in place of the stub.

**Scoring.** Each archetype product is scored on four dimensions — Environment (16 PEF impact
categories), Animal Welfare (2 indices), Social (14 indicators), and Health (derived from the
Nutri-Score). Each dimension is aggregated into a single score, then min–max normalised across the
whole corpus and inverted so that higher always means more sustainable. The four normalised
dimensions are combined into an overall score by a weighting profile.

**Two profiles.** The *scientific* profile uses WP3's expert weights (0.25 per dimension). The
*personal* profile uses weights derived from the user's completed Sustainable Food Compass attempt.
Both are computed for every product.

**Substitution.** For the archetype the scanned product matched, find the L2 categories that may
substitute for its own, keep only candidates that are at least as good on the *scientific* score —
so personalisation can never steer a user toward something objectively worse — and rank the
survivors three ways: best personal, best scientific, and a combined 0.6·personal + 0.4·scientific.
Each of the three is returned.

## Key Insight: personalisation is cheap

Because personalisation applies at the dimension level only (decision, see below), every raw single
score and every min–max normalisation is **user-independent**. The expensive part of the pipeline is
computed once for the whole 2,451-product corpus and cached for the process lifetime. Personalising
reduces to the final four-term weighted average — four multiply-adds per product, microseconds for
the whole corpus.

This also means the `E`/`AW` outlier thresholds, which are absolute cut-offs calibrated to one
specific weight vector, stay fixed and correct. Were sub-weights ever personalised, the entire
corpus would need re-normalising per user *and* those thresholds would lose their meaning — see
*Provisional & Unresolved*.

## User Stories

1. As a user scanning a product, I want to be told a more sustainable alternative, so I can make a better choice at the shelf.
2. As a user, I want the alternative to reflect what I said matters to me in the compass, so the advice is mine and not generic.
3. As a user, I want to be told when the product I scanned is already a good choice, rather than being pushed a substitute I don't need.
4. As a user, I want an alternative that is a plausible replacement for what I'm buying, not an arbitrary healthier food.
5. As a user, I want to be told how much better the alternative is, so I can judge whether the swap is worth it.
6. As a user who has not completed the compass, I want to still get a scientifically-grounded recommendation, so the feature works before I've answered anything.
7. As a user, I want a recommendation that never steers me toward something objectively worse, even where my own priorities would rank it highly.
8. As a user scanning a product we cannot score, I want to be told plainly that no assessment is available, rather than given a silent or misleading answer.
9. As the platform, I want assessment to stay fast under batch scanning (a whole basket), so the client can assess many products in one call.
10. As the project, I want the backend's output to be verifiable against WP3's R reference, so we can show the port is faithful.

## Implementation Decisions

**Placement & modules** (ADR 0005)
- The engine is a **new framework-free Maven module**, `sustainability-scoring`, under
  `howibuy-container`. It holds the numeric method — aggregation, normalisation, profiles, the
  substitution search — as plain Java with no CDI, no Mutiny, no persistence. It depends only on
  `howibuy-model` (for the shared value types) and JUnit/AssertJ in test scope.
- The rationale for a module rather than a package: the method is the deliverable of a research work
  package, it is verified against an external reference implementation, and it must be testable
  without a container. A module makes the framework-freedom enforceable by the build rather than by
  convention. It is a 14th module under `howibuy-container` (32nd overall), which is a real cost —
  taken deliberately, and not a precedent for splitting out other logic.
- Value types the module and the rest of the system share (`WeightProfile`, `SustainabilityDimension`
  reuse, `SubstitutabilityLevel`) live in `tealhelix-architecture/howibuy-model`, package
  `eu.tealhelix.howibuy.v1.model` / `.types`, consistent with the existing core-domain rule.
- `howibuy-services` depends on the new module and owns everything framework-shaped: the
  `@ApplicationScoped` cache, the DAO calls that feed it, and the wiring into `SingleProductAssessor`.

**Domain & schema**
- One new table, `TH_ARCHETYPE_SUBSTITUTABILITY`, holding the WP3 substitutability matrix in long
  form: `from_category_id`, `to_category_id` (both FK to `TH_ARCHETYPE_CATEGORY`, composite PK) and
  `degree SMALLINT CHECK 0 < degree <= 5`. Read as "*from* may substitute for *to*".
- Only non-zero cells are stored: 2,634 rows of a 124×124 matrix. Absence means not substitutable.
- The matrix is currently perfectly symmetric with an all-5s diagonal. The schema is **directional
  anyway** — a future asymmetric revision must not silently invert. The diagonal is retained, and is
  what makes a self-recommendation (and therefore `GOOD_ENOUGH`) reachable.
- Seeded via `loadData` under `context="appdata"`, following the archetype precedent. The CSV is
  generated by `generate_substitutability_csv.py` in this directory, which resolves L2 category names
  to their seeded UUIDs and fails loudly if the taxonomy and the matrix stop agreeing.
- Changesets go into the existing `20260703_archetype_products.xml` — same epic, per the Liquibase
  convention of merging rather than proliferating files.

**Scoring engine (the new module)**
- Transcribe from the `.Rmd` exactly: the 16 PEF normalization factors, the scientific weight
  vectors for E/AW/S, the Nutri-Score map (A→1.0 … E→0), the `E`/`AW` upper thresholds (4 and 100),
  the 0.25 scientific dimension weights, and the 0.6/0.4 combined ranking weights. Every constant
  carries a comment naming its `.Rmd` origin.
- `WeightProfile` is **fully general**: it carries both the within-dimension sub-weights and the four
  dimension weights. Production wires personal sub-weights equal to scientific ones (see the
  granularity decision), but the general shape costs nothing and buys two things — a golden-fixture
  test can configure WP3's actual personal sub-weight vectors and reproduce their output exactly, and
  if within-dimension personalisation is later confirmed it becomes a configuration change.
- Min–max normalisation follows the `.Rmd` precisely, including its asymmetry: the **max** excludes
  values above the upper threshold, the **min** does not; the result is inverted and floored at 0
  with `max(..., 0)`, so products above the threshold score 0.
- The scored corpus is derived from a sub-weight set and memoised on it. Production has exactly one
  distinct sub-weight set, hence one cached corpus; a test may materialise a second.
- **Normalisation is over the full reference corpus, always.** Scores are only comparable within one
  normalisation, so the corpus is definitional and must never be a filtered subset.
- Ties break on a stable product identifier (`agb_code`). R's `which.max` takes the first row in
  `TH_code` order, which is an implementation artefact, not a decision; we need determinism.

**Personal weights from the compass**
- Derivation: average the 1–5 answers within each dimension, drop `ECONOMIC`, normalise the remaining
  four means to sum to 1. These become `w_E`, `w_AW`, `w_S`, `w_H`.
- `ECONOMIC` is elicited by the compass but **omitted from scoring** — there are no economic
  indicators in the WP3 product data. It stays in the questionnaire unchanged.
- The normalisation makes intensity invisible: all-5s and all-3s both yield flat 0.25 weights. This
  is accepted — both express "no dimension above another", and the ranking is identical either way.
- Derived from the user's most recent **completed** attempt only; in-progress attempts never
  contribute. A user with no completed attempt gets the scientific profile for all three outputs,
  and story 6 holds.
- This lives behind a `PersonalWeightsProvider` seam in `howibuy-services`. It is the only component
  that knows the compass exists; the engine sees a `WeightProfile` and nothing more.
- Because a completed attempt is immutable and followed by a stability window, the derived profile is
  stable and may be cached per attempt id.

**Wiring into assessment**
- `SingleProductAssessor.successfulAssessment` replaces `makeDummyProductAssessmentOutcome` with a
  call into the engine, using the archetype the descent resolved. The descent itself is untouched.
- The archetype's L2 category is its L3 category's parent; the taxonomy is already cached, so this
  needs no extra query. `ArchetypeProduct` gains the fields the engine needs.
- `ProductAssessmentService` already receives the `User`; it must now be threaded down to the
  assessor so the personal profile can be resolved. For a batch, resolve the profile **once per
  request**, not once per product.
- Outcome mapping:
  - best alternative is another product → `SUGGESTION`
  - best alternative is the reference product itself → `GOOD_ENOUGH`
  - reference product is unscoreable, or no candidate survives → `NO_SUGGESTION`
  - the descent already failed → the existing `FAILURE_*` outcomes, unchanged.

**Outcome model**
- `AlternativeForProduct` gains the archetype id (a stable join key — the R output stores only the
  name, which its own README flags as ambiguous) and the reference and alternative overall scores, so
  the client can show the size of the improvement (story 5). `OpenApiContractTest` needs updating.

**Configuration**
- `sustainability.substitutability-level` — `SMALL` / `MEDIUM` / `LARGE`, default `SMALL`.
- `sustainability.substitutability-threshold.{small,medium,large}` — the matrix cut-off per level.
  Provisionally 4 / 3 / 1; see below.
- `sustainability.combined-weight.{personal,scientific}` — default 0.6 / 0.4.
- Static Quarkus config; changing them needs a redeploy.

## Provisional & Unresolved

**The `small`/`medium`/`large` thresholds are a guess.** The `.Rmd` matches the matrix against the
strings `x`/`m`/`v`; the current encoding file uses a numeric 0–5 scale. Cross-tabulating the old and
new encodings over all 15,376 cells shows a re-rating, not a recode — no threshold reproduces the old
buckets, so the mapping is not recoverable from the data. We ship 4 / 3 / 1 as a **configurable,
explicitly-flagged provisional default** pending question 1.1. This is the single value in the system
we know might be wrong; nothing else depends on it structurally, and correcting it is a config change.

**Within-dimension personalisation is assumed absent** (question 2.1). If WP3 confirms it is live,
the Key Insight above no longer holds: the corpus must be re-normalised per user and the `4`/`100`
thresholds must become quantile-based to survive a change of scale. The `WeightProfile` shape is
general precisely so this is a contained change, but it would be a real one — treat it as the trigger
to revisit.

**Fourteen L2 categories are invisible to the algorithm** (question 3.1). The 87 products with no
Nutri-Score get no overall score and are dropped in both directions — they receive no recommendation
and can never be recommended. They are not scattered: they exhaust every wine, spirit, liqueur, cider
and alcoholic mixed drink, and every infant formula and infant food. This spec implements the WP3
behaviour as written (`NO_SUGGESTION`); if WP3 decides these should be scored on E/AW/S with the
health term dropped, that is a follow-up.

**Recommendations are highly concentrated** (question 3.2). An argmax over a coarse partition means
32 distinct products absorb all 2,364 recommendations, with textured soy protein alone at 16.8%. This
is faithful to the method. Returning a ranked top-N instead of the single argmax would let the client
diversify without changing the ranking — deliberately **not** in this spec, pending WP3's answer.

## Testing Decisions

Three tiers, mirroring the SFC precedent, with the emphasis shifted: this feature is overwhelmingly
pure arithmetic, so the centre of gravity is plain unit tests in a module with no container at all.

1. **Engine unit tests** (`sustainability-scoring`, plain JUnit + AssertJ, no CDI, no DB). The bulk of
   the coverage. Hand-built fixtures small enough to compute by hand, covering: each dimension's
   aggregation; the min–max asymmetry (max excludes outliers, min does not); the floor at 0 for
   products above the threshold; the Nutri-Score map including the unscoreable case; profile
   combination; the three ranking criteria; the no-regression filter; the self-recommendation case;
   an empty candidate set; and tie-breaking determinism.
2. **Golden-fixture regression test.** The full 2,451-product corpus against WP3's own R output, once
   `questions-ku-leuven.md` 1.1 and 1.2 are answered. Configured with WP3's actual personal
   sub-weight vectors so the personal and combined columns are comparable. Until that arrives,
   `reference_algorithm.py` in this directory is an independent cross-check — useful, but it is our
   reading of the method, not WP3's, and must not be mistaken for the reference.
3. **DB-detail test** (`@QuarkusTest` + Postgres-only resource). The substitutability seed loads and
   its FKs resolve; the matrix and taxonomy agree on all 124 L2 categories. Extend the existing
   `ArchetypeDataIntegrityTest` rather than adding a class.
4. **Service-level test** (Mockito + Weld, `MockReactivePersistenceContextFactory`). The outcome
   mapping — `SUGGESTION` / `GOOD_ENOUGH` / `NO_SUGGESTION` — and that a user with no completed
   attempt falls back to the scientific profile. Prior art: `SingleProductAssessorTest`,
   `ProductAssessmentServiceImplTest`.

No new end-to-end test: the existing assessment workflow test already covers the HTTP path, and this
change does not alter it.

## Out of Scope

- Changing the AI-driven taxonomy descent in `SingleProductAssessor`, which works today.
- Any change to the compass itself — question content, the `ECONOMIC` dimension, the attempt
  lifecycle. This work only *reads* completed attempts.
- Returning a ranked top-N rather than the single best alternative per criterion (see above).
- Scoring products that have no Nutri-Score on their remaining dimensions (see above).
- Recalibrating the `E`/`AW` outlier thresholds, or making them quantile-based.
- Persisting assessment outcomes, or any history of what was recommended to whom.
- Exposing raw dimension scores through the API — only the overall scores reach the client, via the
  improvement figures on the alternative.
- The frontend.

## Further Notes

- Respect ADR 0005 and the coding conventions
  (`tealhelix-architecture/src/site/markdown/CodingConventions.md`) throughout.
- `CONTEXT.md` needs new glossary entries; the vocabulary here is currently informal. At minimum:
  Archetype Product, Sustainability Dimension (note the deliberate 5-vs-4 mismatch with the compass),
  Single Score, Weighting Profile, Substitutability, Alternative. Do this first — issue 01.
- The four scoring dimensions (E/AW/S/H) and the compass's five (`SustainabilityDimension`, which
  includes `ECONOMIC`) are **different sets** and must not be conflated by a shared enum without a
  deliberate mapping. This is the single most likely place to introduce a subtle bug.
- Column-prefix conventions from WP3 (`E_`, `AW_`, `S_`, `H_`; `_SS`, `_SS_MMnorm`) are research
  notation. Do not carry them into Java names — use the glossary terms.
- Build order follows the module dependency chain: glossary + ADR → `howibuy-model` value types →
  `sustainability-scoring` engine → substitutability schema/seed/DAO → `PersonalWeightsProvider` →
  wiring into the assessor → outcome model + OpenAPI. Test-first throughout.
- `reference_algorithm.py` and `generate_substitutability_csv.py` in this directory are analysis
  tooling, not build inputs. The generated seed CSV *is* a build input and is committed.
