# 06 — Compass overview: progress, estimate, scale labels, eligibility

Spec: `.scratch/sustainable-food-compass/spec.md` · ADR 0002, 0003.

**What to build:** A user sees a single overview of where they stand: every category, their overall
and per-category progress, the estimated time to complete overall and per category, the five scale
option labels in their language, their current attempt status, and whether they are allowed to start a
new attempt. This is the capstone slice; it also carries the one end-to-end happy-path test.

**Blocked by:** 02, 04, 05.

**Status:** ready-for-agent

- [x] One overview read (honouring `?lang`) returns the categories with overall and per-category progress (answered/total and percentage) measured against the user's current attempt.
- [x] The overview returns an estimated completion time overall and per category, computed as `sfc.seconds-per-question` × the relevant question count; `sfc.seconds-per-question` is configurable.
- [x] The overview returns the five scale option labels localized for `?lang`, served from a resource bundle keyed by the scale enum; the bundle's languages stay in sync with `sfc.languages`.
- [x] The overview reports the current attempt status and whether the user is eligible to start a new attempt.
- [x] The single Keycloak-backed end-to-end `@QuarkusTest` covers the main happy path only — fetch the localized structure, answer through a category via next-question, complete, then observe the locked state and the overview — and is kept lean because Keycloak startup is slow; finer cases stay at the faster seams.
