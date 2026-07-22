# 03 — Start an attempt and answer a question (immediate save)

Spec: `.scratch/sustainable-food-compass/spec.md` · ADR 0003.

**What to build:** An authenticated user can answer a question by choosing a point on the 1–5 scale,
and the answer is saved the instant it is made. Answering for the first time starts their attempt;
re-answering a question overwrites the previous choice; leaving and returning preserves everything
answered so far.

**Blocked by:** 02.

**Status:** ready-for-agent

- [ ] An attempt is persisted per user, referencing the user by raw UUID with a DB-level FK to the user profile and no JPA association (ADR 0001); it has a status (`IN_PROGRESS`/`COMPLETED`) and a completion timestamp, and at most one in-progress attempt per user is enforced at the database level.
- [ ] An answer is persisted per `(attempt, question)` with an ordinal value constrained to 1–5; the `ScaleOption` enum (five ordered points) exists in `sfc-model`.
- [ ] Answering a question upserts the answer on the user's current in-progress attempt and persists immediately; the in-progress attempt is created lazily on the user's first answer.
- [ ] Re-answering a question replaces the prior value; an out-of-range value is rejected.
- [ ] A user's answers survive across sessions, so they can pause and resume.
- [ ] Endpoints require an authenticated end-user; save-and-overwrite behaviour is covered at the DB-detail seam.
