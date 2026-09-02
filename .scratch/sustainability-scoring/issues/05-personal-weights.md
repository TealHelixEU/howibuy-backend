# 05 — Personal weights from the Sustainable Food Compass

Spec: `.scratch/sustainability-scoring/spec.md`.

**What to build:** The seam that turns a user's completed compass attempt into a `WeightProfile`.
The only component that knows the compass and the scoring engine both exist.

**Blocked by:** 02.

**Status:** done

- [x] `PersonalWeightsProvider` in `howibuy-services` returns a `WeightProfile` for a user.
  - `howibuy-services/.../services/v1/impl/PersonalWeightsProvider.java:29-30,49-54` — the `@ApplicationScoped` bean and its single public method `forUser(User) : Uni<WeightProfile>`.
  - Class javadoc :19-28 — "the only place that knows the compass and the scoring engine both exist", which is the issue's framing sentence.
- [x] Derivation: mean of the 1–5 answers per dimension → drop `ECONOMIC` → normalise the remaining four means to sum to 1 → `w_E`, `w_AW`, `w_S`, `w_H`.
  - `PersonalWeightsProvider.java:75-84` — `meanAnswerPerScoredDimension`, the mean step.
  - `PersonalWeightsProvider.java:36,86-93` — `SCORED_DIMENSIONS`, where dropping `ECONOMIC` happens: it is simply absent from the map, and :80 skips anything the map does not name. The ADR 0005 rationale is the field javadoc at :31-35.
  - `PersonalWeightsProvider.java:60-62` — the normalisation (mean / total).
- [x] Only the user's most recent **completed** attempt is read; an in-progress attempt never contributes.
  - This bullet is satisfied almost entirely on the SFC side — the provider never sees an attempt at all.
- [x] A user with no completed attempt gets the scientific profile, so all three recommendations are still produced.
  - `PersonalWeightsProvider.java:51-53` — `.orElseGet(ScientificWeights::profile)`.
  - Method javadoc :45-48 — why: the recommendations are still produced, just not yet theirs.
  - There is a second fallback the bullet does not mention: PersonalWeightsProvider.java:58 returns the scientific profile when a scored dimension has no answers at all (a partial grouping, e.g. every animal-welfare question retired). Covered by PersonalWeightsProviderTest.java:103. Worth noting as scope beyond the checklist.
- [x] Within-dimension sub-weights are set equal to the scientific ones. This is the assumed granularity (`questions-ku-leuven.md` 2.1) and is commented as such — the general `WeightProfile` shape is what makes revisiting it a configuration change.
  - `PersonalWeightsProvider.java:65` — `.indicatorWeights(ScientificWeights.profile().getIndicatorWeights())`.
  - **Gap**: the bullet says "is commented as such" with a pointer to questions-ku-leuven.md 2.1. It is not. `PersonalWeightsProvider.java:64-67` carries no comment, and the class javadoc :25-27 explains the dimension scaling, not the sub-weight assumption. The test's message ("the compass sets how the dimensions weigh against each other, nothing finer") is the only place the assumption is written down, and a test message is not where a reader of the production code will look. The box is ticked; the comment is missing. Small fix if you want it.
- [x] The uniform-answer case is covered by test: all-5s and all-3s both yield flat 0.25 weights, deliberately.
  - Emergent from `PersonalWeightsProvider.java:60-62` — equal means divide to equal shares — plus the class avadoc :25-27, which states the invisibility of intensity is deliberate rather than a rounding accident.
- [x] ~~The derived profile is cached per completed-attempt id.~~ **Dropped.** Resolving the profile costs three
      queries whatever happens, since learning the attempt id *is* the read; the cache only skipped the four means and
      four divisions, at the price of unbounded mutable state on an `@ApplicationScoped` bean and two tests that could
      only assert "the cache is a cache". The real requirement — resolve once per request for a batch — belongs at the
      call site in issue 06.
- [x] Reads the compass through its existing service interface, not by reaching into SFC tables.
  - `PersonalWeightsProvider.java:14,38,41-43,50` — the only SFC type it holds is `CompassReadService`; there is no DAO, no entity, no `ReactivePersistenceContext` in the class.
  - `howibuy-services/pom.xml:36` — sfc-service-interfaces, deliberately not sfc-dao.
  - Enforced negatively: `PersonalWeightsProviderTest.java:51-55` mocks only `CompassReadService`, so a class that reached past the interface could not be constructed by `@InjectMocks` without the test changing.
