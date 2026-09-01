# The sustainability scoring method lives in a framework-free module, personalised only at the dimension level

The WP3 scoring and substitution method — indicator aggregation into Single Scores, corpus min–max
normalisation, Weighting Profiles, and the substitution search — is implemented in a new Maven
module `sustainability-scoring` under `howibuy-container`, as plain Java with no CDI, no Mutiny and
no persistence. It depends on `howibuy-model` and, in test scope, JUnit and AssertJ. Everything
framework-shaped — the corpus cache, the DAO calls that feed it, the derivation of a personal
profile from the compass, and the wiring into `SingleProductAssessor` — stays in `howibuy-services`.

A user's compass answers personalise the **four dimension weights only**. The weights applied to
Indicators *within* a dimension are WP3's, identical for every user.

## Considered Options

### Where the method lives

- **A new framework-free module (chosen).** The method is the deliverable of a research work
  package, transcribed from an R reference and verified against it. It must be runnable and
  debuggable without a container, and it must stay free of anything that would make that hard. A
  module makes framework-freedom enforceable by the build rather than by convention, and gives the
  golden-fixture regression test somewhere to live that costs no startup.
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
  so sub-weights could only be invented. Pending confirmation from WP3.
- **Indicator weights too.** Defensible if WP3's own personal profile turns out to vary them, and
  the reason the weight-profile type carries sub-weights at all rather than only the four dimension
  weights. Not adopted without that confirmation.

## Consequences

- A 14th module under `howibuy-container`, 32nd in the build. A real cost, taken deliberately for
  the reason above; it is not a precedent for splitting out other logic.
- Value types shared with the rest of the system stay in `tealhelix-architecture/howibuy-model`,
  per the existing core-domain rule; the new module holds no persistence or transport types.
- Because sub-weights are the same for everyone, **every Single Score and every Normalised Score is
  user-independent**. The whole corpus is scored and normalised once and cached for the process
  lifetime; personalising a product is four multiply-adds. The absolute outlier thresholds the
  normalisation uses are calibrated to one weight vector and remain valid for the same reason.
- The four Scored Sustainability Dimensions are not the compass's five Sustainability Dimensions;
  the glossary keeps them as separate terms so the code keeps them as separate types —
  `ScoredSustainabilityDimension` and the compass's `SustainabilityDimension`, mapped explicitly
  where they meet. `ECONOMIC` is elicited and discarded when a personal profile is derived, because
  the product data carries no economic indicator.
- Only one component knows both contexts: the seam in `howibuy-services` that turns a completed
  Attempt into a Weighting Profile. The engine never learns the compass exists.
