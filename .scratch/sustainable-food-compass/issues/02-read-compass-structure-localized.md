# 02 — Read the compass structure in a language

Spec: `.scratch/sustainable-food-compass/spec.md` · ADR 0002.

**What to build:** A user can retrieve the compass's fixed structure in their chosen language — the
categories (localized name, rich description, video link, and detailed-description link) and the
ordered questions within each category, both per category and across all categories. Omitting the
language yields the configured default; an unsupported language fails cleanly rather than returning
half-translated content.

**Blocked by:** 01.

**Status:** ready-for-agent

- [ ] Schema exists for categories and questions with their localized text split into `*_TEXT` tables keyed by `(entity, lang)`: category text carries name, description (rich text) and both links; question text carries the prompt. Questions are ordered by position within a category (unique per category). Naming/constraint conventions are followed.
- [ ] The `SustainabilityDimension` enum (the five fixed dimensions) exists in `sfc-model`; every category carries its dimension, with no unique constraint on dimension.
- [ ] The fixed content is seeded via Liquibase `loadData` from CSV under the `appdata` context — a base CSV per table plus one text CSV per supported language.
- [ ] A read returns all categories localized for `?lang`; a read returns a category's ordered questions localized; a read returns all questions across all categories localized.
- [ ] Omitting `?lang` returns the configured default language; a language outside `sfc.languages` returns 400 with no partial content.
- [ ] Behaviour is covered at the DB-detail seam (Postgres-only test resource): localized reads and the unsupported-language 400.
