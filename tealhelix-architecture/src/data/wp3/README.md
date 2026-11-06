# WP3 inputs

The files WP3 (KU Leuven) provided for the sustainability scoring and recommendation, kept as received and under WP3's
names, so that each can be traced back to the version WP3 refers to. How each is used is in the spec
`tealhelix-architecture/src/site/markdown/specs/002-sustainability-scoring/spec.md`.

- `TH_WP3-FULL DATABASE-v2026-10-27.csv`: the archetype database, the SAFAD taxonomy and one row per Archetype Product
  with its indicators and Nutri-Score. Latin-1. Input of `src/scripts/generate-archetype-data.js`.
- `Step1_encoding_v2026-05-28.csv`: the substitutability matrix between the SAFAD Level 2 categories, degrees 0–5.
  Latin-1. Input of `src/scripts/generate_substitutability_csv.py`.
- `paper_analysis_2026_07_31_v15.qmd`: WP3's analysis notebook, version 15, the source of truth for the method in its
  default configuration. It reads the 07-14 database, which differs from 10-27 only in the Nutri-Score points of the
  two waters.
- `CORE_scores_by_profile.csv`: the notebook's scores of every product of the 10-27 database, once per normalisation
  profile, computed with all 2,451 products in the percentile anchors. Its `log_tails` rows are the expected values of
  the engine's parity test; `src/scripts/generate-scoring-parity-fixture.js` joins them with the database into the
  test's fixture.
- `TH_Algorithm_Implementation_v2026-05-20.Rmd`: WP3's first method, which the notebook replaces.
