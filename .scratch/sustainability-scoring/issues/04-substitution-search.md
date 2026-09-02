# 04 — The substitution search

Spec: `.scratch/sustainability-scoring/spec.md`.

**What to build:** The algorithm's four steps, in the engine module. Given a reference product and a
scored corpus, produce the three best alternatives.

**Blocked by:** 02, 03.

**Status:** done

- [x] Step 1 — eligibility: the L2 categories whose substitutability degree for the reference product's L2 category meets the configured level's threshold.
  - `SubstitutabilityMatrix.java:36-43` — `categoriesSubstitutableFor(categoryId, minimumDegree)`, the degree filter itself.
  - `SubstitutabilityMatrix.java:21-29` — `of(pairs)`, indexing by `toCategoryId` so the lookup key is the scanned product's category.
  - `SubstitutionSearch.java:63` — the call site, passing `settings.minimumDegree()`.
  - `SubstitutablePair.java:11` — the cell record; absence means "not substitutable at any level".
- [x] Step 2 — no regression: keep only candidates whose **scientific** overall score is `>=` the reference product's. The personal profile affects ranking only, never eligibility.
  - `SubstitutionSearch.java:62-69` — `candidatesFor`, specifically line 64 (`referenceScore` computed with `scientificProfile`) and line 67 (>=).
  - The rationale is in the class javadoc, `SubstitutionSearch.java:18-20`.
- [x] Step 3 — rank: `combined = 0.6 × personal + 0.4 × scientific`, both weights configurable.
  - `SubstitutionSearch.java:71-74` — `combinedScore`.
  - `SubstitutionSettings.java:1-64` — the two weights are record components (:153-154 in the concatenated listing; locally `personalWeight`/`scientificWeight`), defaulted at `defaults()`, `SubstitutionSettings.java:31-37`.
- [x] Step 4 — the best candidate under each of the three criteria is returned separately (best personal, best scientific, best combined); they frequently differ.
  - `SubstitutionSearch.java:51-55` — three `best(...)` calls over one candidate list.
  - `Alternatives.java:15-19` — the three `Optional<ScoredProduct>` components.
- [x] The reference product is itself an eligible candidate (its own category is always substitutable for itself, and step 2 is inclusive). When it wins, that is reported distinctly from a suggestion — the caller maps it to `GOOD_ENOUGH`.
  - `SubstitutionSearch.java:58-61` — the javadoc stating why; the behaviour is emergent from :66-67 (its own category is in the eligible set, and >= is inclusive), not a special case.
  - `Alternatives.java:8-9` — how a caller tells the two apart: compare against `reference()`.
  - Caveat: the `GOOD_ENOUGH` mapping itself does not exist yet — that half of the sentence is issue 06. The  engine only makes the distinction expressible.
- [x] An empty candidate set is reported distinctly, not as an error.
  - `SubstitutionSearch.java:49` — `if (candidates.isEmpty()) return Alternatives.none(reference);`
  - `Alternatives.java:21-23` — `none(reference)`; note it keeps the reference, so the answer still says what was searched for.
- [x] Ties break on `agb_code`, so repeated runs over the same corpus give identical results.
  - `SubstitutionSearch.java:81-85` — `best(...)`; the `.thenComparing(comparing(agbCode).reversed())` is the tie-break, reversed because max is taking the largest.
- [x] Substitutability level thresholds come from configuration and default to 4 / 3 / 1. The default is **provisional** pending `questions-ku-leuven.md` 1.1 and is commented as such at its definition.
  - `SubstitutionSettings.java:31-37` — `defaults()`, with the provisional-cut-off javadoc immediately above it (:24-30), including the 15,376-cell cross-tab reasoning and the pointer to question 1.1.
  - `SubstitutionSettings.java:39-48` — compact constructor, rejecting a map missing any level.
  - `SubstitutionSettings.java:53-55,57,61` — `at(level)`, `minimumDegreeFor(level)`, `minimumDegree()`.
- [x] Unit tests: eligibility at each level; the no-regression filter excluding a personally-preferred but objectively worse candidate; the three criteria diverging; self-recommendation; empty candidate set; tie determinism.
