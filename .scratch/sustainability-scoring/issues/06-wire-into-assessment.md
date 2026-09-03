# 06 — Wire the engine into product assessment

Spec: `.scratch/sustainability-scoring/spec.md`.

**What to build:** Replace the stub. `makeDummyProductAssessmentOutcome` disappears and real
recommendations reach the API.

**Blocked by:** 04, 05.

**Status:** done

- [x] An `@ApplicationScoped` holder loads the archetype corpus and the substitutability matrix once and caches them for the process lifetime, following the `FoodTermGlossary` precedent. `ArchetypeCorpus` holds one memoised `Uni`, and unlike the glossary it drops a *failed* load so the next request retries rather than leaving the application permanently unable to recommend anything.
- [x] `SingleProductAssessor.successfulAssessment` calls the engine with the archetype the descent resolved. The descent itself is unchanged.
- [x] The archetype's L2 category is derived from its L3 parent through the cached taxonomy — no extra query per assessment. ~~`ArchetypeProduct` carries whatever the engine needs.~~ **Done differently:** the L2 category is resolved in the corpus query itself (a join to the leaf's parent), so there is no per-assessment derivation to cache a taxonomy for. `ArchetypeProduct` needed no new field — its id is all the engine wants; the new `ArchetypeProductImpacts` carries the impacts and the L2 category.
- [x] `makeDummyProductAssessmentOutcome` is deleted.
- [x] ~~The `User` is threaded from `ProductAssessmentService` down to the assessor.~~ The resolved `WeightProfile` is threaded instead: the service resolves it once and hands the assessor a value, which is what makes once-per-request structural rather than a convention the assessor has to honour. For a batch, the personal profile is resolved **once per request**, not once per product.
- [x] Outcome mapping: another product → `SUGGESTION`; the reference product itself → `GOOD_ENOUGH`; unscoreable reference or empty candidate set → `NO_SUGGESTION`; a failed descent → the existing `FAILURE_*` outcomes, unchanged.
- [x] `AlternativeForProduct` gains the archetype id and the reference and alternative overall scores; `OpenApiContractTest` is updated.
- [x] Service-level tests (Mockito + Weld) cover each outcome mapping and the no-completed-attempt fallback to the scientific profile. The mappings are covered in `ScoredArchetypesTest`, which needs no container because the class is a plain value; `SingleProductAssessorTest` (Weld) covers `SUGGESTION` and `NO_SUGGESTION` reaching the outcome, and the scientific-profile fallback stays covered by `PersonalWeightsProviderTest`.
- [x] Assessing a batch issues no per-product database query for scoring. `ArchetypeCorpusTest.readsTheCorpusAndTheMatrixOnceHoweverOftenItIsAsked` counts the DAO calls; `ProductAssessmentServiceImplTest.resolvesTheUsersWeightingProfileOncePerBatchRatherThanOncePerProduct` counts the compass read.

## Decisions taken here, not dictated by the spec

- **Which score each alternative reports.** The bullet asked for "the reference and alternative overall scores" without
  saying under which weighting. Each of the three alternatives reports both scores under the criterion that chose it —
  the user's weights for the personal one, WP3's for the scientific one, the 0.6/0.4 blend for the combined one — so the
  pair explains why that product won that ranking and the alternative never appears to score below the reference. The
  blend formula moved to `SubstitutionSettings.combinedScore` so the caller reports the same number the search ranked by
  rather than restating 0.6/0.4.
- **The substitutability level is still `SubstitutionSettings.defaults()`.** Reading it from configuration was issue
  04's bullet and is satisfied by the type; nothing yet injects a configured value, so every assessment runs at `SMALL`.
