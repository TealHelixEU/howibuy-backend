# 01 — Glossary entries and ADR 0005 (module placement)

Spec: `.scratch/sustainability-scoring/spec.md`.

**What to build:** The shared vocabulary and the recorded architecture decision, before any code.
The scoring domain is currently spoken about only in WP3's research notation (`E_SS_MMnorm`), which
must not leak into Java names.

**Blocked by:** nothing.

**Status:** done

- [x] `CONTEXT.md` gains a "Product Sustainability Assessment" section with entries for: Archetype Product, SAFAD Taxonomy (L1/L2/L3), Single Score, Normalised Score, Weighting Profile (scientific / personal), Substitutability, Substitutability Level, Alternative, Reference Product. Each with the `_Avoid_` list, matching the existing style.
- [x] The glossary states explicitly that the four **scoring** dimensions (Environment, Animal Welfare, Social, Health) are not the compass's five `SustainabilityDimension` values, and that `ECONOMIC` is elicited but not scored for want of data.
- [x] ADR 0005 records the decision to place the engine in a new framework-free `sustainability-scoring` Maven module: context (a research method verified against an external reference), the options weighed (package inside `howibuy-services`; new module; inline in the assessor), the decision, and the consequences — including that the build, not convention, enforces framework-freedom.
- [x] ADR 0005 also records the dimension-level-only personalisation decision and its consequence (the corpus normalisation is user-independent and cacheable), with the trigger that would force a revisit.
- [x] No production code in this issue.
