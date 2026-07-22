# TealHelix — Claude project notes

Multi-module Maven backend for TealHelix (sustainable-food labelling). Java 25, Quarkus, Hibernate Reactive on Postgres,
RESTEasy Reactive, Qute, Keycloak for IDM, Liquibase for schema.

For build/run/DB details, see [README.md](README.md) and [PORTS.md](PORTS.md). This file is for the things that aren't
obvious from reading the code or the README.

## Required Reading
Before making code changes, read:
- `tealhelix-architecture/src/site/markdown/CodingConventions.md`

Treat `tealhelix-architecture/src/site/markdown/CodingConventions.md` as authoritative for coding style and conventions.

## Module layout

```
tealhelix/
├── tealhelix-architecture/        Pure domain model (entities, value types). No framework deps.
│   └── howibuy-model/            HowiBuy domain entities
│   └── howibuy-model-json/       Map the HowiBuy model to JSON with Jackson
│   └── src
│       └── site
│           └── markdown
│               └── adr           The directory to put Architecture Decision Records
├── tealhelix-common/              Cross-microservice infrastructure. No Quarkus deps outside deployment modules.
│   ├── tealhelix-common-types/    Shared value types (Email, UserId, ...)
│   ├── tealhelix-common-dao-reactive/         Persistence abstraction interfaces (ReactivePersistenceContext, ...)
│   ├── tealhelix-common-dao-reactive-hibernate/   Hibernate-Reactive implementation of the above
│   ├── tealhelix-common-services/             Service interfaces shared across microservices (UserService, ...)
│   ├── tealhelix-common-services-impl/        Implementations of the above
│   ├── tealhelix-common-web/                  JAX-RS infrastructure (JwtAuthenticationFilter, TokenHelper, ...)
│   └── tealhelix-common-testutils/            Test resources & utilities (Postgres/Keycloak Testcontainers, @InjectPostgres, ...)
├── howibuy-container/            The HowiBuy microservice
│   ├── howibuy-dao/              DAO interfaces
│   ├── howibuy-dao-hibernate-reactive/   Hibernate-Reactive DAO impls + Liquibase changelog
│   ├── howibuy-service-interfaces/       Service interfaces
│   ├── howibuy-services/                 Service implementations
│   ├── howibuy-jaxrs/                    JAX-RS resources
│   ├── howibuy-testutils/                Module-specific test utilities
│   └── howibuy/                          Quarkus deployable (ties everything together)
└── tealhelix-docker/              Dockerfiles + docker-compose
    ├── tealhelix-docker-postgres/
    └── tealhelix-docker-keycloak/
```

Dependency rule: only the `*-deployable` modules (`howibuy/`) and `*-testutils` may depend on Quarkus. Everything else
stays framework-agnostic so the artifacts remain reusable. When upgrading a dependency whose version is dictated by
Quarkus, look for the XML comment marker next to its `version.*` property in the root [pom.xml](pom.xml) and bump the
bundle together.

## Persistence

- All persistence goes through `ReactivePersistenceContextFactory.withTransaction(tx -> ...)` or
  `.withoutTransaction(em -> ...)`. Don't reach for `Mutiny.SessionFactory` directly outside
  `tealhelix-common-dao-reactive-hibernate`.
- DAO methods take a `ReactivePersistenceContext` / `ReactivePersistenceTxContext` as their first parameter — the
  service composes the transaction boundary, the DAO doesn't.
- IDs follow a pattern: a domain interface (`UserId`) + an implementation (`UserIdImpl`) + test utilities
  (`UserIdTestUtils`). Same for `RetailerId`, `Email`, etc. Don't introduce a new ID type without all three.
- An external IDM identifier (the `sub` claim from Keycloak) is **not** the same as the internal `UserId`.
  `UserService.requireUserFromValidIdmId` translates IDM → internal; `requireUserWithId` is for already-internal IDs
  (e.g. impersonation tokens we issued ourselves). `TokenHelperImpl` picks between the two based on whether the JWT is
  impersonated.

## Liquibase

- Changelog root: `howibuy-container/howibuy-dao-hibernate-reactive/src/main/resources/db.changelog.xml`, including
  per-version files under `changelogs/v1.0.0/`.
- **For changes that belong to the same epic/ticket, merge new changesets into the existing per-epic file rather than
  creating a new one.** Minimize the number of new changeset files.
- The `dev` context loads development seed data — activate with `-Dliquibase.contexts=dev` on the `dbupdate-howibuy` profile.

## Running things

Common invocations that actually work (the project has install-skipped Docker modules, so `-am` from the project root is usually necessary):

```bash
# Build everything (no Docker images)
mvn package

# Build everything including Docker images
mvn package -Pdocker

# Run a single test in howibuy (note -am to pull in the install-skipped tealhelix-docker-* modules,
# and -Dsurefire.failIfNoSpecifiedTests=false so upstream modules without a matching test don't fail the run)
mvn test -pl howibuy-container/howibuy -am \
    -Dtest=CorrelationIdWorkflowTest \
    -Dsurefire.failIfNoSpecifiedTests=false

# Apply Liquibase to a local DB (needs a Maven profile in ~/.m2/settings.xml — see README)
mvn process-resources -Pdbupdate-howibuy,th-local-postgres

# Dev mode for HowiBuy
mvn -Phowibuy-quarkus-dev,th-local-postgres
```

Ports (see [PORTS.md](PORTS.md)): HowiBuy HTTP 8180 / test 8181 / debug 5105; Keycloak 8280.

## Testing

- Unit tests for services use **Mockito + Weld JUnit5 (`@EnableAutoWeld`, `@AddBeanClasses`)**, with
  `MockReactivePersistenceContextFactory` standing in for the DB. Use this style when the system under test is mostly
  programmatic logic.
- Integration / workflow tests use `@QuarkusTest` with `PostgresAndKeycloakTestResource` (Testcontainers Postgres + a
  real Keycloak container). Use this style when the DB interactions are non-trivial. Example: `CorrelationIdWorkflowTest`.
- `@InjectPostgres` / `@InjectKeycloak` inject the running containers when you need to talk to them directly (e.g., to
  fetch a real access token from Keycloak before hitting the app).
- Don't mock the DB when the test's purpose is to verify DB behavior, even though Mockito is faster — accuracy beats
  speed in those cases.

## Versions & dependency upgrades

- Every dependency version lives as a `version.<id>` property in the root [pom.xml](pom.xml). Add new ones the same way.
- Check out `README.md`, section "Updating dependencies" for how to update dependencies.
- Quarkus-coupled versions are marked with an XML comment; upgrade them together with the Quarkus platform, not independently.

## Authentication flow (so you know what you're looking at)

```
Request → JwtAuthenticationFilter (nonBlocking, event loop)
            → TokenHelper.processToken
                ├── service token  → UserImpl built from JWT claims (no DB)
                ├── impersonated   → UserService.requireUserWithId (DB lookup by internal UserId)
                └── normal user    → UserService.requireUserFromValidIdmId (DB lookup by IDM `sub`)
         → SecurityContext set
         → resource method (Uni-based ⇒ event loop, plain return ⇒ worker thread)
```

The `/tokenexchange` endpoint mints an impersonation JWT signed by us (see `JwtGenerationService`); the
`JwtAuthenticationFilter` recognises it via `isImpersonated(...)` and takes the second branch above.

## Data Objects Between Layers
Any data object shared internally between the DAO and service layers belongs in the `howibuy-services-model`
module (package `eu.tealhelix.howibuy.services.model.*`).
DAO interfaces in `howibuy-dao` may return these types directly. Do not place such carriers in the `howibuy-dao`
module itself.

Distinguish three homes for data types:
- **Core domain model** — types central to the domain (e.g. `ProductData`) live in `tealhelix-architecture`,
  `howibuy-model`, package `eu.tealhelix.howibuy.v1.model`). These are the model the whole system is built around.
- **Service-interface return/parameter types** — types returned or accepted by a service interface (in
  `eu.tealhelix.howibuy.services.v1`) that are NOT part of the core model belong in `eu.tealhelix.howibuy.services.v1.types`.
  The `v1` package holds the service interfaces; `v1.types` holds the peripheral data types they exchange.
- **DAO↔service carriers** — internal, not exposed by any service interface: `howibuy-services-model` as above.
- **Types shared by both the DAO and a service interface** — a generic data type returned by a DAO *and* carried in a
  service-interface type can live in neither `services-model` (service-interfaces must not depend on it) nor `v1.types`
  (the DAO must not depend on service-interfaces). Put such a type in `tealhelix-common-types` (`eu.tealhelix.common.types`).
  Keep it generic — no feature-specific wording — since it sits in a foundational module.

## Log/exception messages
Try to place variable content of the message to be logged/used in an exception at the end of the message. E.g.,
instead of "Product ${id} not found", use "Product not found, id: ${id}" or similar. This way when we see a message in
the logs, we can search for it in the code more easily.
