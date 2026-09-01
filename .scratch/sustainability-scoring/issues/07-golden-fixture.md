# 07 — Golden-fixture regression against the WP3 R output

Spec: `.scratch/sustainability-scoring/spec.md`.

**What to build:** Proof that the port is faithful to WP3's own run, not merely to our reading of
the `.Rmd`.

**Blocked by:** 04. **Externally blocked** on `questions-ku-leuven.md` 1.1 (the level thresholds) and
1.2 (the reference output). Everything else can ship without this; this is what lets us *claim* the
port is correct.

**Status:** blocked

- [ ] WP3's `alg_db` output is committed as a test fixture: `TH_code`, both overall scores, and the three `alt_best_*` columns for all rows, together with a note recording the exact input files and `level` used to produce it.
- [ ] The engine is configured for the test with WP3's **actual** personal sub-weight vectors (E's 16, AW's 0.75/0.25, S's with the two zeros), so the personal and combined columns are comparable despite production's dimension-level-only decision.
- [ ] Overall scientific and personal scores match to a documented tolerance, with the tolerance justified rather than tuned until green.
- [ ] All three `alt_best_*` recommendations match per product. Any divergence is investigated and either fixed or recorded as a deliberate, explained difference — never silently tolerated.
- [ ] The test names what it is pinning: agreement with an external reference implementation, not the correctness of the method.
- [ ] `reference_algorithm.py` is **not** the fixture source. If it disagrees with WP3's output, it is the script that is wrong.
