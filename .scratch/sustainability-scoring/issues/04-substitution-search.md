# 04 — The substitution search

Spec: `.scratch/sustainability-scoring/spec.md`.

**What to build:** The algorithm's four steps, in the engine module. Given a reference product and a
scored corpus, produce the three best alternatives.

**Blocked by:** 02, 03.

**Status:** done

- [x] Step 1 — eligibility: the L2 categories whose substitutability degree for the reference product's L2 category meets the configured level's threshold.
- [x] Step 2 — no regression: keep only candidates whose **scientific** overall score is `>=` the reference product's. The personal profile affects ranking only, never eligibility.
- [x] Step 3 — rank: `combined = 0.6 × personal + 0.4 × scientific`, both weights configurable.
- [x] Step 4 — the best candidate under each of the three criteria is returned separately (best personal, best scientific, best combined); they frequently differ.
- [x] The reference product is itself an eligible candidate (its own category is always substitutable for itself, and step 2 is inclusive). When it wins, that is reported distinctly from a suggestion — the caller maps it to `GOOD_ENOUGH`.
- [x] An empty candidate set is reported distinctly, not as an error.
- [x] Ties break on `agb_code`, so repeated runs over the same corpus give identical results.
- [x] Substitutability level thresholds come from configuration and default to 4 / 3 / 1. The default is **provisional** pending `questions-ku-leuven.md` 1.1 and is commented as such at its definition.
- [x] Unit tests: eligibility at each level; the no-regression filter excluding a personally-preferred but objectively worse candidate; the three criteria diverging; self-recommendation; empty candidate set; tie determinism.
