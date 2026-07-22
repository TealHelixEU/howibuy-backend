# SFC content is internationalized with per-entity translation tables

Multilingual SFC content is stored as a language-neutral base table plus a `*_TEXT` child table
keyed by `(entity_id, lang)` (e.g. `TH_SFC_CATEGORY(id, dimension)` +
`TH_SFC_CATEGORY_TEXT(category_id, lang, name, description, video_url, detail_url)`). The requested
language arrives as an explicit `?lang=xx` query param on the read endpoints, defaulting to a
configured default language. The set of supported languages is a configured closed set; content is
complete by construction (the seed covers every supported language) — guaranteed by the content
authors, with neither a runtime check nor an automated test enforcing it. A request for an
unsupported language returns 400, and there is no silent per-field fallback. The residual risk of a
bad seed reaching users unnoticed is accepted because the content is fixed and curated.

## Considered Options

- **Per-entity translation tables (chosen).** Boringly-correct relational i18n: base attributes
  stored once, only text per-language, real FKs and a `UQ(entity_id, lang)`. Seeds cleanly via
  Liquibase `loadData` CSV, one text CSV per language (mirroring the `food_term_gr.csv` precedent).
- **JSONB `lang → text` map column.** Rejected: the codebase has no JSONB anywhere, filtering by
  language needs JSON operators, reads pull the whole blob, and it is awkward to seed via `loadData`.
- **Generic localized-string table** (`owner_type, owner_id, field, lang, text`). Rejected:
  stringly-typed, no column types, no FK integrity.
- **`Accept-Language` header** for language selection. Rejected: no existing handling to hook into,
  fiddlier to parse, and it clashes with the codebase convention that locale is an explicit input
  (`ProductData.language`) rather than server-resolved.

## Consequences

- The DB translation-table approach applies to curated *content* (categories, questions). A fixed,
  closed set of *UI strings* — the Likert scale labels — is localized instead via a resource bundle
  keyed by an enum, not DB tables. The rule: content that is authored/curated lives in DB
  translation tables; a fixed chrome string set lives in a resource bundle. The scale therefore has
  no `TH_SFC_SCALE_OPTION*` tables; an answer stores the raw ordinal (`SMALLINT`, `CHECK 1..5`). The
  bundle's languages must stay in sync with `sfc.languages`, guaranteed by the content authors.
- Links (`video_url`, `detail_url`) are treated as localized and live in the text table, so
  `TH_SFC_CATEGORY` holds only `(id, dimension)`.
- Answers are language-independent (an ordinal), so `lang` affects only the read/display path.
- New configuration: the supported-language set and the default language (e.g. `sfc.languages=en,el`,
  `sfc.default-language=en`).
