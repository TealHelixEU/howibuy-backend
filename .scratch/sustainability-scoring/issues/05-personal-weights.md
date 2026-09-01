# 05 — Personal weights from the Sustainable Food Compass

Spec: `.scratch/sustainability-scoring/spec.md`.

**What to build:** The seam that turns a user's completed compass attempt into a `WeightProfile`.
The only component that knows the compass and the scoring engine both exist.

**Blocked by:** 02.

**Status:** ready-for-agent

- [ ] `PersonalWeightsProvider` in `howibuy-services` returns a `WeightProfile` for a user.
- [ ] Derivation: mean of the 1–5 answers per dimension → drop `ECONOMIC` → normalise the remaining four means to sum to 1 → `w_E`, `w_AW`, `w_S`, `w_H`.
- [ ] Only the user's most recent **completed** attempt is read; an in-progress attempt never contributes.
- [ ] A user with no completed attempt gets the scientific profile, so all three recommendations are still produced.
- [ ] Within-dimension sub-weights are set equal to the scientific ones. This is the assumed granularity (`questions-ku-leuven.md` 2.1) and is commented as such — the general `WeightProfile` shape is what makes revisiting it a configuration change.
- [ ] The uniform-answer case is covered by test: all-5s and all-3s both yield flat 0.25 weights, deliberately.
- [ ] The derived profile is cached per completed-attempt id (a completed attempt is immutable and followed by a stability window, so it cannot go stale).
- [ ] Reads the compass through its existing service interface, not by reaching into SFC tables.
