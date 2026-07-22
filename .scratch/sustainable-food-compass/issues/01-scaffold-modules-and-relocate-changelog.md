# 01 — Scaffold SFC modules & relocate the Liquibase changelog root

Spec: `.scratch/sustainable-food-compass/spec.md` · ADR 0001.

**What to build:** The foundation for the Sustainable Food Compass bounded context. Stand up the SFC
Maven modules and wire them into the existing `howibuy` deployable, and relocate the aggregating
Liquibase changelog so that both HowiBuy's and SFC's schema are applied from one root owned by the
deployable. No user-facing behaviour yet — this is the prefactor that de-risks the changelog move and
lets every later slice land cleanly on green.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] `sfc-model` exists in `tealhelix-architecture`; `sfc-dao`, `sfc-dao-hibernate-reactive`, `sfc-service-interfaces`, `sfc-services`, and `sfc-jaxrs` exist under `howibuy-container`, each with a pom following the project's dependency grouping/ordering conventions and the correct inter-module dependencies (mirroring the HowiBuy layering).
- [ ] The `howibuy` deployable depends on the SFC runtime modules so their beans and JAX-RS resources are picked up when the app boots.
- [ ] `sfc-dao-hibernate-reactive` has no compile dependency on any `howibuy-*` module (the bounded-context boundary is real, per ADR 0001).
- [ ] The aggregating `db.changelog.xml` lives in the `howibuy` deployable and includes the existing HowiBuy per-version changelog plus a new, initially empty, SFC changelog whose changesets are owned by `sfc-dao-hibernate-reactive`.
- [ ] Both the Quarkus runtime Liquibase and the `dbupdate-howibuy` Maven profile apply the complete schema from the relocated root; a fresh DB comes up identically to before.
- [ ] `mvn package` succeeds and the entire existing test suite stays green; the app boots and migrates unchanged.
