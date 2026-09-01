# Questions for KU Leuven (WP3) — porting the scoring & substitution algorithm to the backend

We are implementing the WP3 food sustainability scoring and substitution algorithm
(`TH_Algorithm_Implementation_v2026-05-20.Rmd`) in the TealHelix backend, so it runs per user, per
scanned product, in production. Porting it from a batch research script to an interactive service
surfaces a number of choices the `.Rmd` did not have to make.

Below, **Tier 1** blocks correctness and we cannot answer it ourselves. **Tier 2** are decisions we
have provisionally taken so work is not blocked — we would like them confirmed or corrected.
**Tier 3** are confirmations we are fairly confident about.

The measurements quoted throughout come from a Python re-implementation of the `.Rmd` run over the
2,451-product dataset as seeded into our database. It reproduces the pipeline as written; it is not
a run of your R code. See the appendix.

---

## Tier 1 — Blocking

### 1.1 What do the 0–5 values in `Step1_encoding_v2026-05-28.csv` mean, and where do `small` / `medium` / `large` cut?

The algorithm selects eligible substitutes with `matrix_A[, subcategory_A] %in% level`, where

```r
small  <- c("x")
medium <- c("x", "m")
large  <- c("x", "m", "v")
```

The current encoding file uses a numeric 0–5 scale instead of `x`/`m`/`v`, so as written every
eligibility test returns `FALSE` and every recommendation comes back `NA` — silently, with no error.

We cannot recover the mapping from the data. Cross-tabulating the old `Step1_encoding.csv` against
the new file over all 15,376 cells shows a **re-rating, not a recode** — no threshold reproduces the
old buckets:

| old | new 0 | 1 | 2 | 3 | 4 | 5 |
|-----|------:|--:|--:|--:|--:|--:|
| `x` |     0 |  0 |  70 | 456 | 524 | 328 |
| `m` |    20 | 24 | 148 | 244 |  60 |   0 |
| `v` |     8 |194 | 360 |   4 |  12 |   0 |
| (blank) | 12714 | 62 | 62 | 82 | 4 | 0 |

**Questions:**

- What does each value 0–5 denote? Is it an ordinal similarity/substitutability rating, and is 5 the
  most substitutable?
- Which cut-offs correspond to `small`, `medium` and `large`?
- Is the three-level concept still current, or has it been superseded by the finer 0–5 scale?
- Which level is intended as the production default?

Until we hear back we are proceeding with a **provisional, clearly-flagged** mapping of
`small ≥ 4`, `medium ≥ 3`, `large ≥ 1`, held in configuration so a correction is a config change.

### 1.2 Can we have the reference output of your run?

We would like to verify our port against your implementation rather than against our reading of it.

Ideally: the `alg_db` table after the algorithm chunk — `TH_code`, `overall_scientific_SS`,
`overall_personal_SS` and the three `alt_best_*` columns for all rows — as CSV, together with the
exact input files and the `level` setting used to produce it.

If that is inconvenient, confirming the exact input filenames of your last good run would already
let us reproduce it ourselves. (Note the `.Rmd` loads `Step1_encoding.csv`, which now exists only in
`Old files/`, while the current encoding is `Step1_encoding_v2026-05-28.csv` — we assume the latter
is intended, but the two are not interchangeable, per 1.1.)

---

## Tier 2 — Provisional decisions we would like confirmed

### 2.1 Is personalisation intended at the dimension level only?

The `.Rmd` defines `personal` weights at two levels: the four overall dimension weights
(`E`/`AW`/`S`/`H` at 0.1/0.1/0.1/0.7), **and** within each dimension — 16 environmental sub-weights,
`AW` at 0.75/0.25, and a social vector in which `S_forced_labour` and `S_corruption` are set to `0`.

Our questionnaire (the Sustainable Food Compass) elicits attitudes at the **dimension** level only.
It has no instrument at the resolution of individual PEF impact categories or individual social
indicators, and we do not think one is realistic to ask a consumer to complete.

We also note your sensitivity analysis sweeps only the four dimension weights (the 1,771-scenario
grid over `w_E, w_AW, w_S, w_H`), which suggests within-dimension personalisation may not be a live
part of the method.

**Questions:**

- Is dimension-level personalisation the intended scope, with sub-weights fixed at their scientific
  values for every user? That is our assumption.
- If within-dimension personalisation *is* intended, where should the values come from?
- What is the provenance of the personal sub-weight vectors in the `.Rmd` — a pilot survey, an
  illustrative example, or something else? In particular, are the zeros on `S_forced_labour` and
  `S_corruption` deliberate?

This matters more than it looks. If sub-weights are fixed, every raw single score and every min–max
normalisation is user-independent and can be computed once for the whole corpus; personalisation
reduces to the final four-term weighted average. If they are not, the entire corpus must be
re-normalised per user, which also destabilises the outlier thresholds — see 2.3.

### 2.2 Deriving dimension weights from questionnaire answers

The compass records, per user, a 1–5 Likert answer to each question, with questions grouped into one
category per dimension. Our intended derivation:

1. Average the 1–5 answers within each dimension.
2. Drop `ECONOMIC` — we have no economic indicators in the product data, so it cannot enter scoring.
   (It stays in the questionnaire.)
3. Normalise the remaining four means to sum to 1; these become `w_E`, `w_AW`, `w_S`, `w_H`.

**Questions:**

- Is a normalised mean the derivation you would endorse, or do you have a defined instrument for
  this?
- The normalisation makes intensity invisible: a user answering 5 to everything and a user answering
  3 to everything both receive flat 0.25 weights. We are treating that as acceptable — "cares about
  everything equally" and "cares about nothing in particular" produce the same ranking either way.
  Do you agree?
- `ECONOMIC` is elicited but discarded. Is that acceptable, or should it be surfaced differently to
  the user so they are not answering questions that have no effect?

### 2.3 The `E` and `AW` outlier thresholds

`upper_threshold` is set to `4` for `E` and `100` for `AW`, excluding products above it when
computing the max for min–max normalisation (products above it then floor to 0).

These are absolute cut-offs on the *unnormalised* single score, whose scale is set by
`1000 × weight / normalization_factor`. They are therefore calibrated to one specific weight vector.

**Questions:**

- How were `4` and `100` chosen? Are they intended as fixed constants of the method, or as
  properties of the current dataset that should be recalibrated when the data changes?
- If 2.1 is confirmed (sub-weights fixed), this is a non-issue and we will hardcode them. If
  within-dimension personalisation is live, would you accept replacing them with a quantile-based
  cut (e.g. the 99th percentile), which is scale-free and survives a change of weights?

---

## Tier 3 — Confirmations

### 3.1 Fourteen L2 categories can never receive or be offered a recommendation

87 products carry `H_nutriscore = '0'`. These get `NA` for health, hence `NA` overall, and are
dropped by `filter(!is.na(overall_scientific_SS))`. They are therefore invisible to the algorithm in
both directions: they receive no recommendation, and they can never be recommended.

Those 87 are not scattered — they exhaust **14 entire L2 categories**:

```
Spirits (12)              Wine (8)                  Wine-like drinks / cider (6)
Alcoholic mixed drinks(4) Liqueur (2)               Baking ingredients (3)
Food for weight reduction (4)
Ready-to-eat meal for infants and young children (14)
Cereal-based food for infants and young children (11)
Yoghurt/cheese/milk-based dessert for infants and young children (7)
Follow-on formulae, powder (3)   Follow-on formulae, liquid (2)
Infant formulae, liquid (2)      Infant formulae, powder (1)
```

So a user scanning any wine, any spirit or any infant formula gets no output at all, even though the
substitutability matrix has non-zero entries for those categories (wine ↔ beer is 5, wine ↔ water
is 2).

**Questions:**

- Is this the intended behaviour, or an artefact of Nutri-Score's exclusions?
- For alcohol, "no alternative offered" may well be the right policy — but is it a *deliberate*
  policy, or an accident we should not rely on?
- Infant formula seems the more serious case: it is a category where parents may most want guidance,
  and it is silently empty.
- Would you accept scoring these products on `E`/`AW`/`S` only, with the health term dropped and the
  remaining weights renormalised, so they at least participate?

### 3.2 Only 32 distinct products are ever recommended

Because step 4 takes a single `which.max` over each compatible set, the 2,364 scoreable products
collapse onto a very small recommendation vocabulary. At the provisional `small` level:

| share | recommended product |
|------:|---------------------|
| 16.8% | Soy protein, textured, rehydrated, from soy flour |
| 10.1% | Bread, wholemeal or integral bread |
|  9.2% | Bogue, raw |
|  9.1% | Haricot beans with tomato sauce, canned |
|  9.1% | Beetroot, raw |
|  6.7% | Tofu, plain |
|  4.8% | Tap water |

**32 distinct products** account for all 2,364 recommendations, and 16.9% of recommendations cross
into a different L1 category.

This is mathematically correct — it is what an argmax over a coarse partition does — but as a
consumer-facing product it means a large fraction of users are told to eat textured soy protein.

**Questions:**

- Is this concentration expected and acceptable for the scientific method?
- Would you object to the application returning the top *N* alternatives rather than the single
  argmax, so the client can diversify? The ranking would be unchanged; we would just not discard the
  runners-up.
- Is a recommendation that crosses L1 (e.g. a beverage suggested in place of a meat product)
  intended?

### 3.3 Behaviours we have read off the code — please confirm

- **Self-recommendation.** The diagonal of the matrix is 5, so a product's own category is always
  eligible, and step 2 keeps candidates scoring `>=` the reference. A product that is the best in its
  own compatible set therefore recommends itself. We surface that as "your product is already a good
  choice" rather than as a suggestion. It occurs for 1.0% of products at the `small` level.
- **The no-regression guard is objective.** Step 2 filters on `overall_scientific_SS` only;
  the personal profile affects ranking, never eligibility. So personalisation can never recommend
  something objectively worse than the reference product. We assume this is deliberate.
- **Ranking weights.** `weight_personal = 0.6`, `weight_scientific = 0.4` for the combined criterion.
  Fixed constants of the method, or configurable?
- **Ties.** `which.max` returns the first maximum in row order, i.e. in `TH_code` order — an
  implementation detail rather than a decision. We will break ties on a stable product identifier so
  results are reproducible. Is any tie-break meaningful to the method?
- **Matrix symmetry.** The current encoding is perfectly symmetric (all 15,376 cells), with a
  diagonal of 5. The `.Rmd` reads it column-wise (`matrix_A[, subcategory_A]`), which only matters if
  it stops being symmetric. Is symmetry a property you intend to maintain, or an incidental feature
  of the current version?
- **The normalisation corpus is definitional.** Min–max normalisation runs over whatever set of
  products is loaded, so scores shift if the product database is extended or filtered. We will always
  normalise over the full reference dataset, never over a subset. Confirming this is right.

---

## Appendix — how the figures above were produced

A Python re-implementation of the `.Rmd` pipeline, run over the same 2,451-product dataset as seeded
into our database (`archetype_product.csv`, derived from `TH_WP3-FULL DATABASE-v2026-05-27.csv`) and
`Step1_encoding_v2026-05-28.csv`. It applies the PEF aggregation, the min–max normalisation with the
`4`/`100` thresholds, the Nutri-Score map, the 0.25 scientific and 0.1/0.1/0.1/0.7 personal
dimension weights, and the four algorithm steps exactly as written in the `.Rmd`.

Sanity checks that match the `.Rmd` documentation: 2,451 products; 87 with `nutri_score = '0'`
dropped; Nutri-Score distribution 810 A / 221 B / 464 C / 492 D / 377 E; 124 L2 categories in both
the taxonomy and the matrix.

Outcome distribution by level:

| level | real suggestion | recommends itself | no candidate |
|-------|----------------:|------------------:|-------------:|
| `small` (≥4)  | 2,340 (99.0%) | 24 (1.0%) | 0 |
| `medium` (≥3) | 2,354 (99.6%) | 10 (0.4%) | 0 |
| `large` (≥1)  | 2,357 (99.7%) |  7 (0.3%) | 0 |

Because these rest on the provisional threshold mapping of 1.1, they should be read as indicative of
the *shape* of the results, not as reproductions of your numbers.
