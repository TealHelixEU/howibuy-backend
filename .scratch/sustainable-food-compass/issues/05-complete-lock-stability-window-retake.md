# 05 — Complete, lock, stability window, re-take

Spec: `.scratch/sustainable-food-compass/spec.md` · ADR 0003.

**What to build:** A user who has answered everything can explicitly complete the compass; if anything
is unanswered they are told exactly which questions remain. On success their attempt becomes an
immutable record. They cannot begin another attempt until the configured stability window has passed;
once it has, their next answer starts a fresh, blank attempt, and the previous record is untouched.

**Blocked by:** 03.

**Status:** ready-for-agent

- [ ] Completion is a strictly explicit action; it succeeds only when every question in every category is answered, otherwise it returns 422 with the unanswered question IDs.
- [ ] On success the attempt is stamped with its completion time and transitions to `COMPLETED`; any further answer write to a completed attempt is refused (immutable record).
- [ ] Eligibility to start a new attempt is computed on read as completion time + `sfc.stability-window`; while inside the window, answering with no in-progress attempt is refused with 409.
- [ ] Once the window has elapsed, the next answer starts a new attempt that is blank (no answers carried forward); the previous completed attempt remains unchanged and forms a history.
- [ ] Time flows through `DateTimeService` (extended if an accessor is needed) so the window is testable without sleeping; `sfc.stability-window` is configurable.
- [ ] Covered at the DB-detail seam with controlled time: incomplete→422, success→lock, immutability of a completed attempt, within-window 409, and post-window fresh blank attempt.
