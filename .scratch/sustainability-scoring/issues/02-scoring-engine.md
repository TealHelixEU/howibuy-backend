# 02 — The scoring engine: dimensions, normalisation, profiles

Spec: `.scratch/sustainability-scoring/spec.md` · ADR 0005.

**What to build:** The new `sustainability-scoring` module and the scoring half of the method — raw
indicators in, an overall score per product per profile out. No persistence, no CDI, no Mutiny.

**Blocked by:** 01.

**Status:** done

- [x] New Maven module `howibuy-container/sustainability-scoring`, depending only on `howibuy-model` plus JUnit/AssertJ in test scope. The build fails if a framework dependency is added.
- [x] Value types in `tealhelix-architecture/howibuy-model`: `ScoredSustainabilityDimension` (`ENVIRONMENT`/`ANIMAL_WELFARE`/`SOCIAL`/`HEALTH` — named in full, never shortened, and never merged with the compass's `SustainabilityDimension`; see ADR 0005), `WeightProfile` (within-dimension sub-weights **and** the four dimension weights), and `SubstitutabilityLevel` (`SMALL`/`MEDIUM`/`LARGE`).
- [x] WP3's constants are transcribed with a comment naming their `.Rmd` origin: the 16 PEF normalization factors, the E/AW/S scientific weight vectors, the Nutri-Score map (A→1.0, B→0.75, C→0.5, D→0.25, E→0), the upper thresholds (E 4, AW 100), the 0.25 dimension weights.
- [x] Aggregation per dimension: environment applies `1000 × weight / normalization_factor` per PEF; animal welfare and social apply plain weights.
- [x] Min–max normalisation reproduces the `.Rmd` asymmetry exactly: the **max** excludes values above the upper threshold, the **min** does not; the result is inverted (higher = more sustainable) and floored at 0.
- [x] A product with no Nutri-Score has no overall score under any profile, and is excluded from the scored corpus — matching R's `NA` propagation and `filter(!is.na(...))`.
- [x] The scored corpus is derived from a sub-weight set and memoised on it; normalisation is always over the whole corpus supplied, never a subset.
- [x] Unit tests on hand-computed fixtures: each dimension's aggregation; the max-excludes/min-includes asymmetry; the floor at 0 above threshold; each Nutri-Score grade and the unscoreable case; the overall weighted average under both profiles.
