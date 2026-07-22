# SFC lives as a bounded context inside the HowiBuy deployable, in its own Maven modules

The Sustainable Food Compass (SFC) — a multilingual questionnaire whose per-user results feed
product assessment — is built as a separate bounded context (`eu.tealhelix.sfc.*`) but deployed
inside the existing `howibuy` Quarkus application, sharing its datasource and Keycloak client.
Its code lives in dedicated Maven modules that mirror the HowiBuy layering rather than in the
`howibuy-*` modules.

## Considered Options

- **Bounded context inside `howibuy` (chosen).** No second deployable, DB, port, or Keycloak
  client. SFC results are consumed by product assessment, so the data coupling is real and a
  separate service would only add operational cost.
- **Separate `sfc` microservice.** Cleaner isolation, independent release cadence — rejected as a
  premature complexity tax given the assessment coupling and the absence of an independent-scaling
  or independent-team driver. The module split below keeps this option cheap to revisit.

## Consequences

- New Maven modules under `howibuy-container`: `sfc-dao`, `sfc-dao-hibernate-reactive`,
  `sfc-service-interfaces`, `sfc-services`, `sfc-jaxrs`. `sfc-services-model` is created only if a
  DAO↔service carrier type actually appears. SFC's framework-free domain model lives in
  `tealhelix-architecture` as `sfc-model` (package `eu.tealhelix.sfc.v1.model`), mirroring
  `howibuy-model`.
- Module names stay honest (no SFC types inside `howibuy-*`), the bounded-context boundary is
  enforced at compile time, and a future extraction into its own deployable is mostly "add a
  deployable + datasource".
- Because Quarkus runs one Liquibase changelog per datasource and SFC shares the datasource, the
  aggregating root `db.changelog.xml` moves to the `howibuy` deployable module and `<include>`s
  both HowiBuy's and SFC's per-module changelogs. Each module owns only its own changesets.
- SFC references the HowiBuy-owned user by ID only: `TH_SFC_ATTEMPT.user_id` is a raw `UUID` with a
  DB-level FK to `TH_USER_PROFILE(id)` and no JPA association. This keeps the module boundary real
  rather than nominal (`sfc-dao-hibernate-reactive` stays free of HowiBuy code) and makes a future
  extraction "drop the FK". It deliberately diverges from `ConsentEntity`'s `@ManyToOne`, which is
  correct there because consent is within the HowiBuy context.
