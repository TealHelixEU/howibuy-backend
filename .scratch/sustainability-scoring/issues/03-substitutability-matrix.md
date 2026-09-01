# 03 — The substitutability matrix: schema, seed, DAO

Spec: `.scratch/sustainability-scoring/spec.md`.

**What to build:** WP3's 124×124 substitutability matrix as queryable reference data.

**Blocked by:** 01.  (Independent of 02 — can proceed in parallel.)

**Status:** done

- [x] `TH_ARCHETYPE_SUBSTITUTABILITY`: `from_category_id`, `to_category_id` (both FK to `TH_ARCHETYPE_CATEGORY`, composite PK), `degree SMALLINT` with `CHECK degree > 0 AND degree <= 5`. Constraint and index naming per the coding conventions.
- [x] Changesets are **merged into the existing** `20260703_archetype_products.xml`, not a new file — same epic, per the Liquibase convention.
- [x] The seed CSV is produced by `generate_substitutability_csv.py` and committed under `db/archetype/`; loaded via `loadData` under `context="appdata"`, with a `<rollback>` delete.
- [x] Only non-zero cells are stored (2,634 rows); absence means not substitutable. The all-5s diagonal **is** stored — it is what makes a self-recommendation reachable.
- [x] The relation is stored directionally, read as "*from* may substitute for *to*", matching the `.Rmd`'s column-wise lookup. The current matrix is symmetric; the schema does not assume it stays so.
- [x] `SubstitutabilityDao` returns the whole matrix in one query (it is small reference data read as a unit, never per-row).
- [x] `ArchetypeDataIntegrityTest` is extended: every seeded row's FKs resolve, every degree is 1–5, and the 124 L2 categories in the taxonomy and the matrix agree exactly in both directions.
