# 06 — Wire the engine into product assessment

Spec: `.scratch/sustainability-scoring/spec.md`.

**What to build:** Replace the stub. `makeDummyProductAssessmentOutcome` disappears and real
recommendations reach the API.

**Blocked by:** 04, 05.

**Status:** ready-for-agent

- [ ] An `@ApplicationScoped` holder loads the archetype corpus and the substitutability matrix once and caches them for the process lifetime, following the `FoodTermGlossary` precedent.
- [ ] `SingleProductAssessor.successfulAssessment` calls the engine with the archetype the descent resolved. The descent itself is unchanged.
- [ ] The archetype's L2 category is derived from its L3 parent through the cached taxonomy — no extra query per assessment. `ArchetypeProduct` carries whatever the engine needs.
- [ ] `makeDummyProductAssessmentOutcome` is deleted.
- [ ] The `User` is threaded from `ProductAssessmentService` down to the assessor. For a batch, the personal profile is resolved **once per request**, not once per product.
- [ ] Outcome mapping: another product → `SUGGESTION`; the reference product itself → `GOOD_ENOUGH`; unscoreable reference or empty candidate set → `NO_SUGGESTION`; a failed descent → the existing `FAILURE_*` outcomes, unchanged.
- [ ] `AlternativeForProduct` gains the archetype id and the reference and alternative overall scores; `OpenApiContractTest` is updated.
- [ ] Service-level tests (Mockito + Weld) cover each outcome mapping and the no-completed-attempt fallback to the scientific profile.
- [ ] Assessing a batch issues no per-product database query for scoring.
