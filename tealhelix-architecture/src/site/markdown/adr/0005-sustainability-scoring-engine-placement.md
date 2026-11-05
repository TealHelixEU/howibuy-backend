# The sustainability scoring method lives in a framework-free module, personalised only at the dimension level

The WP3 scoring and substitution method — indicator aggregation into Single Scores, percentile
normalisation, Weighting Profiles, and the substitution search — is implemented in a new Maven
module `sustainability-scoring` under `howibuy-container`, as plain Java with no CDI, no Mutiny and
no persistence. It depends on `howibuy-model` and, in test scope, JUnit and AssertJ. Everything
framework-shaped — the corpus cache, the DAO calls that feed it, the derivation of a personal
profile from the compass, and the wiring into `SingleProductAssessor` — stays in `howibuy-services`.

The method is that of WP3's analysis notebook `paper_analysis_2026_07_31_v15.qmd` in its default
configuration, and the engine is verified against the scores the notebook computes.

A user's compass answers personalise the **four dimension weights only**. The weights applied to
Indicators *within* a dimension are WP3's, identical for every user.

## Considered Options

### Where the method lives

- **A new framework-free module (chosen).** The method is the deliverable of a research work
  package, ported from an R reference and verified against it. It must be runnable and
  debuggable without a container, and it must stay free of anything that would make that hard. A
  module makes framework-freedom enforceable by the build rather than by convention, and gives the
  parity test against WP3's scores somewhere to live that costs no startup.
- **A package inside `howibuy-services`.** Cheaper — no new module — but framework-freedom would
  rest on reviewer discipline alone, next to code that is legitimately full of CDI and Mutiny. For
  ordinary logic this would be the right call; the deciding factor here is the external reference
  implementation, which makes isolated verification a first-class requirement rather than a
  preference.
- **Inline in `SingleProductAssessor`.** Rejected outright: the assessor already owns the AI-driven
  taxonomy descent, and the two have nothing to do with each other.

### How far personalisation reaches

- **Dimension weights only (chosen).** The compass elicits attitudes at the granularity of a
  dimension; it asks nothing that could distinguish one environmental impact category from another,
  so sub-weights could only be invented. WP3 confirmed that its own personalisation acts at the
  dimension level only.
- **Indicator weights too.** Rejected on that confirmation. A Weighting Profile is the four
  dimension weights and nothing else; the indicator weights are constants of the engine.

### How the Single Scores are normalised

- **Percentile min–max, log-transformed where the scores are skewed (chosen).** The notebook's
  normalisation profile `log_tails`: each dimension's Single Scores are rescaled between their 1st
  and 99th percentile, clamped at both ends, and inverted so that higher means more sustainable;
  Animal Welfare and Socio-economic, whose Single Scores are heavily skewed, are first
  log-transformed around the median of their positive values. Health's Single Score is the
  Nutri-Score Points, normalised as Environment is. Percentile anchors need no hand-set cut-offs,
  and both transforms are scale-free, so they hold for any data version WP3 sends.
- **Plain min–max with absolute outlier cut-offs, and Health read off the Nutri-Score letter.** WP3's
  first method, which the notebook replaces. Its cut-offs were calibrated to one data version and one
  weight vector, and five evenly spaced letters cannot tell apart two products of the same grade.
- **The notebook's other profiles** (z-score, rank, a log transform of every dimension, and their
  variants). WP3 compared them and chose `log_tails` as its default; the engine has no reason to
  differ.

Archetype Products without a Nutri-Score, such as infant food, have no Overall Score. They are left
out of the scale altogether, as WP3 decided, so that they move neither its anchors nor its log
offsets.

### How the port is verified

- **Parity on the scores (chosen).** A test scores all 2,451 products of WP3's 10-27 database and
  compares every Normalised Score and the scientific Overall Score with the scores the notebook
  writes (`CORE_scores_by_profile.csv`, profile `log_tails`) within `1e-9`, and the gate outcome of
  every product. WP3 computed those scores with the 47 products without a Nutri-Score inside the
  scale, so the test scores the corpus that way, through a package-private entry point; another
  test fails should the production entry point stop leaving them out.
- **Parity on the recommended products as well.** Dropped: the notebook draws its Alternative at
  random, so only the scores and the gate the draw is made from are compared.

## Consequences

- A 14th module under `howibuy-container`, 32nd in the build. A real cost, taken deliberately for
  the reason above; it is not a precedent for splitting out other logic.
- Value types shared with the rest of the system stay in `tealhelix-architecture/howibuy-model`,
  per the existing core-domain rule; the new module holds no persistence or transport types.
- Because the indicator weights are the same for everyone, **every Single Score and every
  Normalised Score is user-independent**. The corpus is scored and normalised once and cached for
  the process lifetime; personalising a product is four multiply-adds.
- The four Scored Sustainability Dimensions are not the compass's five Sustainability Dimensions;
  the glossary keeps them as separate terms so the code keeps them as separate types —
  `ScoredSustainabilityDimension` and the compass's `SustainabilityDimension`, mapped explicitly
  where they meet. Socio-economic covers WP3's social data, which includes economic indicators (a
  fair salary, fair competition, the contribution to economic development), so the compass's Social
  and Economic dimensions both bear on its weight.
- Only one component knows both contexts: the seam in `howibuy-services` that turns a completed
  Attempt into a Weighting Profile. The engine never learns the compass exists.
- The parity fixture is generated from WP3's files by
  `tealhelix-architecture/src/scripts/generate-scoring-parity-fixture.js`. A new data version or a
  new notebook from WP3 means a new fixture, and the test then tells at once whether the engine
  still agrees.
