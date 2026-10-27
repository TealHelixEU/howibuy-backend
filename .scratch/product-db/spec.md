# Retailer Catalogues and offline Classification

Status: ready-for-agent

Domain vocabulary follows the project glossary (`tealhelix-architecture/src/site/markdown/CONTEXT.md`, sections
"Product Sustainability Assessment" and "Retailer Catalogue"). The architecture is decided in ADR `0006`
(`tealhelix-architecture/src/site/markdown/adr/0006-retailer-catalogues-and-offline-classification.md`). This spec must
be implemented consistently with it.

## Problem Statement

As a retailer, I want my customers to get an assessment of a scanned product, plus better products from *my own
shelves*, fast enough to show inline in my e-shop. Today HowiBuy classifies every scanned product with an LLM at request
time. That is too slow for the retailer's page, and its answers are archetypes ("a generic yoghurt"), not products the
customer can put in the basket.

## Solution

Retailers send us their product data per Catalogue (one per country e-shop). A separate **Ingestion** deployable stores
each Feed, parses it with a per-Retailer adapter, keeps the current state of every Retailer Product and Listing in its own
database, and exposes what changed through an ordered cursor. A **Catalogue Sync** component carries those changes into
HowiBuy. HowiBuy keeps the products, has them classified offline (LLM, an expert or a person) to at most one Archetype Product
each, and answers an assessment by looking the scanned product up. The three Alternatives become real Retailer Products,
listed and active in the user's Catalogue.

```
Retailer ──Feed──▶ Ingestion (own DB) ──changes after N──▶ Catalogue Sync ──apply──▶ HowiBuy
                    store raw, parse,                       (stateless;             products, listings,
                    diff, deactivate                         run by hand at first)   classifications
                                                                   │
                                          classify pending ◀───────┘  (LLM runs inside HowiBuy)
User (via retailer token: Retailer + Catalogue) ──assess──▶ HowiBuy: lookup → scoring → product selection
```

## User Stories

1. As a retailer, I want to push a Feed for one of my Catalogues and be answered immediately with an Ingestion Run id, so my export job is never held up by our processing.
2. As a retailer, I want to query the status of an Ingestion Run, with its counts and the records rejected and why, so I can fix my export.
3. As a retailer, I want to send a Snapshot, a Delta or a Deletion Feed, depending on what my systems can produce.
4. As a retailer, I want a product that disappears from my Snapshot to stop being recommended, and to come back without delay when it reappears.
5. As a retailer, I want a truncated or broken Snapshot to not wipe out my Catalogue.
6. As a retailer, I want the same product sold in several countries to be recognised as one product, with its own price in each Catalogue.
7. As a retailer, I want to name the Catalogue when I exchange a token, so that assessments use the right assortment and prices.
8. As a retailer's customer, I want the assessment of a scanned product to come back fast.
9. As a retailer's customer, I want the Alternatives to be products I can actually buy in this e-shop, with their name in my language and their price.
10. As a retailer's customer scanning a product that is no longer listed, I still want it assessed.
11. As a retailer, when a product is not assessed, I want to know whether it is unknown, not classified yet, or matches no archetype.
12. As an operator, I want to classify the pending products at a pace I control, and re-classify selectively after the classifier improves.
13. As an operator, I want an expert or a person to record a Classification, and to have it take precedence over the LLM's.
14. As an operator, I want products whose per-language Classification Attempts disagree to be flagged for review.
15. As an operator, I want to confirm or discard an Ingestion Run held by the sanity check.
16. As an operator, I want every Ingestion Run and every Classification Attempt recorded, so problems can be traced to a file, adapter or classifier version.
17. As an operator, I want to re-run a stored Feed after an adapter fix, without asking the retailer to send it again.
18. As an operator, I want Ingestion to crash without affecting assessments.
19. As an operator, I want to measure the classifier against the eval sets without storing them as products, so I can compare classifier versions before re-classifying.

## Implementation Decisions

### Modules & deployables (ADR 0006)

- **Ingestion** is a new bounded context, `eu.tealhelix.catalogue`, in a new `catalogue-container/` mirroring the HowiBuy
  layering: `catalogue-dao`, `catalogue-dao-hibernate-reactive` (its own Liquibase root, since it has its own database),
  `catalogue-service-interfaces`, `catalogue-services`, `catalogue-feeds` (the per-Retailer adapters, one package per
  Retailer), `catalogue-jaxrs`, `catalogue-testutils` and the Quarkus deployable `catalogue`. A framework-free
  `catalogue-model` goes in `tealhelix-architecture`, if a model type is needed outside the container. Ports: HTTP 8380,
  test 8381, debug 5305 (to be added to `PORTS.md`).
- **Catalogue Sync** is a Quarkus command-mode deployable `catalogue-sync` with two REST clients and no database. At
  first it is run by hand (or from cron); it can later become a long-running scheduler without changing the contracts.
- **HowiBuy** holds Retailer Products, Listings and Classifications in the existing `howibuy-*` modules. They belong to
  the assessment context: assessment reads them, and nothing else does.
- Ingestion's database is a schema and login of its own, `th_catalogue`, in the same `tealhelix` database, isolated
  like `th_howibuy` and `keycloak`: neither login can read the other's schema. The `tealhelix-docker-postgres` image
  creates it on first start.

### Identity

- Retailer: the existing `TH_RETAILER` / `RetailerId` (= the Keycloak client's id). Ingestion keeps its own copy of the
  Retailers and Catalogues it serves (id, name, adapter, languages), keyed by the same ids.
- Catalogue: a new `CatalogueId` value type, with the usual trio `CatalogueId` / `CatalogueIdImpl` /
  `CatalogueIdTestUtils`. It is a short stable code chosen by us, e.g. `rimi-lt`, because retailers type it into requests
  and it has to be identical in both databases. Catalogues are configuration, seeded by Liquibase in both databases.
- Retailer Product: identified externally by `(RetailerId, retailer's product id)`, the latter a string (ProductKey on the
  wire). Internally a UUID each side assigns on its own. The two sides only share the external key.

### Ingestion: receiving a Feed

- `POST /api/v1/catalogues/{catalogueId}/feeds?type=SNAPSHOT|DELTA|DELETION`, body = the raw file, streamed (never
  buffered in memory). Authenticated with the Retailer's service token. The Catalogue must belong to the calling Retailer.
- The body is streamed gzip-compressed to the feed store (a configured directory) while its SHA-256 and size are
  computed. An `IN_INGESTION_RUN` row is created in status `RECEIVED`, and the call answers `202` with the run id and the
  status URL. Processing then starts asynchronously, on a worker thread (StAX parsing is blocking).
- Feeds of one Catalogue are processed strictly one at a time, in order of receipt. A run waits while an earlier run of
  the same Catalogue is processing or held.
- Pull-based Retailers (none yet) would get a scheduled fetcher producing the same stored file and run. The adapter
  contract below keeps fetching separate from parsing, so this is additive.

### Ingestion: the adapter contract

- `FeedReader` (one per Retailer, selected by the Catalogue's configuration): given the stored stream, it emits a stream
  of `FeedRecord`s (retailer product id, names by language, characteristics, tags, price, currency) and of
  `RejectedRecord`s (position, retailer product id if known, reason). It parses and maps, and that is all: no database,
  no diffing.
- `FeedRecord` validation (required fields, a known language, a name in the Catalogue's primary language, a positive
  price, a known ISO currency) is common code after the adapter, so every Retailer is held to the same rules. The
  primary-language name guarantees that every active Listing has a name to fall back to.
- Characteristics and tags are language-neutral and belong to the Retailer Product, not to a language or a Catalogue.
  This holds only if the Feeds deliver them neutrally. If two Catalogues of one Retailer sent localised values for the
  same product, each Feed would overwrite the other's and change the hash, and the product would be classified again on
  every alternate Feed. Each adapter has to show from a real file that the values are neutral. If they are localised,
  characteristics and tags move to a per-language table like names, before the adapter ships.

### Ingestion: applying a Feed

Records are applied in batches (configurable, ~500 per transaction):
- Upsert the Retailer Product. The names of the languages in the record replace those languages' names; names in other
  languages are left alone. Characteristics and tags are replaced.
- Recompute the **classification input hash** (SHA-256 over the canonicalised names, characteristics and tags). Price
  never takes part.
- Upsert the Listing: price, currency, `active = true`, `last_seen_at`, `last_seen_run_id`.
- If anything visible changed (product content, price, currency, a reactivation), stamp the product with the next value
  of the global change sequence (see "Change cursor").

After the whole stream of a **Snapshot**:
- Count the Catalogue's active Listings that this run didn't see. If they exceed the configured share of the Catalogue
  (e.g. 10%), set the run to `HELD` and stop. Otherwise make them inactive and stamp their products.
- `POST /api/v1/ingestion-runs/{id}/confirmation` (operator) runs the deactivation of a held run.
  `POST /api/v1/ingestion-runs/{id}/discard` closes it without deactivation. Products upserted before the hold are kept
  either way, because upserts only add or update.

A **Delta** never deactivates anything. A **Deletion** Feed carries only retailer product ids, and its Listings are made
inactive.

**Replay** (user story 17): `POST /api/v1/ingestion-runs/{id}/replay` (operator) records a new run on the stored Feed of
a `DONE`, `FAILED` or `DISCARDED` run, with `replay_of_run_id` pointing to it, and answers `202` with the new run. The
new run shares the Feed's file and `received_at`, so it queues in the Feeds' order of receipt and its Listings keep the
true `last_seen_at`. It is processed with the adapter the Catalogue has now. The run replayed is never modified. A
Snapshot is refused (`409`) once a later Snapshot of its Catalogue that has not failed exists, because it would take off
sale what the later one carries. Any other replay out of order is the operator's to repair, by replaying the later Feeds
as well, in order. A run that has not finished is refused (`409`), and a Feed no longer stored is `404`.

**Eviction** (scheduled, in Ingestion): a Listing that has been inactive with `last_seen_at` older than the configured
threshold is deleted. A Retailer Product left without Listings becomes a tombstone (`removed = true`, `removed_at`,
stamped) and loses its names. Tombstones are purged after a much longer period, which bounds how far behind Catalogue
Sync may fall. A tombstone that a Feed carries again comes back with the same id. The eviction runs in one transaction
that holds the change sequence from its start, and every batch of a Feed holds it before it reads its products, so
neither changes a product that the other has read.

### Change cursor

- There is no separate log table. Every `IN_RETAILER_PRODUCT` row carries `change_sequence`, assigned from one database
  sequence whenever the product or any of its Listings changes. "Changes after N" =
  `WHERE change_sequence > N ORDER BY change_sequence LIMIT M`.
- Each entry is the **complete current state** of a Retailer Product: external key, names, characteristics, tags, input
  hash, all its Listings (catalogue, price, currency, active), or a tombstone. Applying it is an idempotent upsert, and a
  product changed five times between two reads appears once.
- `GET /api/v1/product-changes?after=N&limit=M` (sync service account) answers the entries and the cursor to continue
  from.
- A stamping transaction holds the change sequence (a transaction-scoped advisory lock) from its first stamp until it
  ends, so transactions commit in the order of their values, and a reader never moves past a value that is not visible
  yet. The batches of the Feeds and the eviction take it at their start (see "Eviction").

### Catalogue Sync

- Stateless. HowiBuy owns the cursor: it records the last sequence applied, in the same transaction as the products it
  applies, so a crash mid-way re-applies at most one page.
- Loop: `GET` HowiBuy's cursor → `GET` changes after it from Ingestion → `POST` the page to HowiBuy → until a page is
  empty.
- It never classifies. Classification is a separate command of the same tool (below), so a full Catalogue is visible
  (and its prices current) long before the LLM gets through it.

### HowiBuy: storing products

- `POST /api/v1/catalogue-changes` (sync service account) with `{fromSequence, toSequence, products[]}`: upserts the
  products and Listings, deletes tombstoned products, and advances the cursor, all in one transaction. A request whose
  `fromSequence` is not the stored cursor is refused (`409`), which keeps two concurrent syncs from interleaving.
  `GET /api/v1/catalogue-changes/cursor` answers the cursor.
- A product whose input hash differs from the hash its Classification was made from becomes **pending**.

### HowiBuy: Classification

- `GET /api/v1/classifications/pending?limit=M`: pending products, oldest change first, with their input hash and the
  languages they are named in.
- `POST /api/v1/llm-classifications` `{retailerProductId, language}`: runs the identification half of the current LLM
  pipeline (glossary, guidance, taxonomy descent) on the product's name in that language, records a Classification
  Attempt and resolves the Classification. Malformed LLM replies (`FAILURE_OTHER`) are not recorded, so the product stays
  pending.
- `POST /api/v1/classification-attempts`
  `{retailerProductId, inputHash, language, classifier, classifierVersion, archetypeProductId | null}`: records an
  attempt made elsewhere (an expert, one of us). `null` means "no matching archetype". `409` when `inputHash` is no
  longer the product's current one, because the product changed while it was being classified.
- Every attempt records its classifier and **classifier version** (LLM model + prompt + glossary version, or the
  source's own label) and the input hash it was made from.
- Resolution policy (`ClassificationResolution`, swappable): consider only the attempts made from the current input
  hash, and of those the latest per classifier and language. A manual attempt beats an expert one, which beats the
  LLM. Among equals, agreement resolves. Disagreement resolves to the attempt
  in the Retailer's classification language (`TH_RETAILER.classification_language`), a column of its own because one
  product can be listed in Catalogues with different primary languages. If there is none (the product is
  not sold in that language), the remaining attempts are ordered by language code. A disagreement always sets
  `needs_review`.
- Re-classification after a classifier improves: `POST /api/v1/classifications/reopening` `{classifier,
  classifierVersion}` (operator) reopens the Classifications that came from that classifier version, which makes their
  products pending, and answers how many. A Classification records the classifier that decided it; it came from the
  versions of that classifier's latest attempts, one per language, made from its input hash. A reopened Classification
  keeps its archetype until an attempt resolves it again.
- The Catalogue Sync tool gets a `classify` command: take pending products, call `llm-classifications` for each language,
  sequentially, with a configurable limit and rate. It prints a summary of the run but stores none: the attempts, with
  their classifier, classifier version, input hash and time, are the record of what was classified. There is no
  classification batch table.
- `POST /api/v1/llm-classification-previews` (operator) `{name, language, characteristics, tags}`: runs the same
  identification code as `llm-classifications` on posted product data, stores nothing, and answers the archetype (or
  the failure) with its diagnostics. The classification eval harness (`assessment-eval.js`) uses it once assessment no
  longer calls the LLM. It authenticates as an operator: a person holding the `operator` role, logged in through
  Keycloak like `keycloak-auth.sh` does.

### HowiBuy: assessment

- `SingleProductAssessor` splits:
  - `ReferenceProductIdentifier`: `(CatalogueId, ProductKey)` → archetype, or a failure reason.
  - The recommendation part (`ScoredArchetypes` and what follows).
  The LLM descent moves behind `llm-classifications` and is no longer on the request path.
- The request: the Catalogue comes from the token, and the body carries product keys and the language for the answer.
  `ProductData` as an input is replaced (breaking, accepted in ADR 0006). The name, characteristics and tags it carries
  are now known from the Feed.
- **Failure reason**, next to `ProductAssessmentOutcome.getType()`, for `FAILURE_TO_IDENTIFY`:
  - `UNKNOWN_PRODUCT`: no such Retailer Product in HowiBuy.
  - `NOT_CLASSIFIED`: known but pending, never classified.
  - `NO_MATCHING_ARCHETYPE`: the resolved Classification says none.
  (Three reasons, not the two discussed: HowiBuy now stores products before classifying them, so it *can* tell
  "unknown" from "not classified yet".)
- An Inactive Listing of the scanned product does not prevent its assessment. A product with no Listing in the user's
  Catalogue but known to the Retailer is assessed too: the Classification belongs to the product.
- A Classification answers with its archetype even when its product is pending again (changed since, or reopened) or
  it needs review. The Catalogue is not checked again at assessment time.
- **Identification diagnostics** come with every outcome but `UNKNOWN_PRODUCT`: the retailer product id, and, once
  classified, the classifier, whether the Classification needs review, whether the product is pending, and the
  archetype (id and name) when there is one.
- A multi request carries at most 400 product keys (`400` otherwise). A key sent twice is answered twice, in the order
  sent. A batch is looked up with one query.
- **Catalogue-aware recommendation.** `SubstitutionSearch.find` takes the set of archetypes stocked in the Catalogue (=
  having at least one active Listing whose product is classified to it), and filters candidates by it *before*
  ranking. The reference archetype always stays a candidate (see `GOOD_ENOUGH`). Query the set per request first, and
  cache per Catalogue only if measurements say so.
- **Choosing the product** (`AlternativeProductSelection`, swappable): given the winning archetype, the Catalogue and
  its active Listings of that archetype, pick one. Initial policy: the lowest price, ties broken by retailer product id
  for determinism.
- `AlternativeForProduct` redesigned:
  - `SUGGESTION`: product key, name in the requested language (falling back to the Catalogue's primary language) and the
    language actually returned, price,
    currency, plus the archetype (id, name, taxonomy path) and the two overall scores.
  - `GOOD_ENOUGH`: archetype and scores only, no product.
  - `NO_SUGGESTION`: nothing.

### Tokens

- `TokenExchangeRequest` gains a required `catalogueId`. It must be an active Catalogue of the calling Retailer (`403`
  otherwise). The token exchange is the workflow that assesses products, so it needs the Catalogue.
- The handoff names no Catalogue: the single-page application it hands the user over to needs only the user, never a
  Retailer's products. Its ticket and session tokens stay as they are.
- The impersonation token carries `catalogue` (and the retailer, already implied). It is never renewed, so no renewal
  has to copy the claim.
- How a request reads it: the Catalogue belongs to the session, not to the user, so it is not put on `User`.
  `TokenHelper` reads it, because only it knows whether a token is one this application signed: the claims alone do
  not say, and the IDM's tokens name no Catalogue whatever claims they carry. `JwtAuthenticationFilter` stores the
  `CatalogueId` as a request property, next to the security context. Only HowiBuy's filter sets that property, so
  reading it stays in `howibuy-jaxrs`, whose `RequestCatalogue.currentCatalogue(crc)` returns `Optional<CatalogueId>`;
  `tealhelix-common-web` knows nothing of claims or Catalogues. Each resource that needs a Catalogue refuses the
  request when there is none. The service layer receives a non-null `CatalogueId` from its caller, like the `User`,
  and never sees the token.
- `TokenHelper.processToken` must hand the filter both the `User` and the Catalogue, so it returns an
  `Authentication` of the two. The `TokenHelper` stubs in the `@QuarkusTest`s (SFC tests, `ClassificationsTest`,
  `CatalogueChangesTest`) change with it.

### Roles

- Every service checks *what* a caller may do through roles, never through "is it a service account". Ingestion has
  no user database, so it validates Keycloak service tokens only. Today
  `authorizeImpersonation` accepts any service account, so any client of the realm counts as a Retailer.
- `tealhelix-common-types` gets an enum `Role { RETAILER, CATALOGUE_SYNC, OPERATOR }`, and `User.getRoles()` returns a
  `Set<Role>`. The roles cross services: Retailers are checked by HowiBuy and Ingestion, Catalogue Sync and operators
  by both. `isRetailer()`, `isCatalogueSync()` and `isOperator()` are `default` methods over the set.
- `TokenHelperImpl` fills the set from the Keycloak realm roles of IDM-issued tokens (`realm_access.roles`), mapping
  `retailer` → `RETAILER`, `catalogue-sync` → `CATALOGUE_SYNC` and `operator` → `OPERATOR` statically. It ignores the
  roles it doesn't know.
  The impersonation tokens HowiBuy mints carry no roles.
- Whether the caller is a person is a separate question from roles: an operator is a person with a role.
  `User.isService()` becomes `isPerson()` (its inverse), and `TealHelixAuthorization.requireUserNotService` becomes
  `requirePerson`.
- `authorizeImpersonation` requires `RETAILER`. The Sync endpoints of both services require `CATALOGUE_SYNC`. Feed
  pushes require `RETAILER`. The operator endpoints (held runs, replays, re-opening, previews) require `OPERATOR`.
- `tealhelix-realm.json` gains the realm roles `retailer`, `catalogue-sync` and `operator`, and a `catalogue-sync`
  client with a service account. The existing `lime_fresh` service account gets `retailer`, and `bob@krusty-krab.com`
  gets `operator` for local use.
- Catalogue Sync gets its token from Keycloak with the client-credentials grant (`quarkus-oidc-client`, which also
  refreshes it during long runs).

### Schema: HowiBuy (in the `howibuy` changelog, one new per-epic file)

| Table                        | Columns (main)                                                                                                                          | Notes                                                                 |
|------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------|
| `TH_CATALOGUE`               | `id` (code), `retailer_id`, `country`, `primary_language`, `active`                                                                     | seeded                                                                |
| `TH_RETAILER_PRODUCT`        | `id`, `retailer_id`, `product_key`, `characteristics`, `tags`, `input_hash`, `change_sequence`                                          | `UQ (retailer_id, product_key)`                                       |
| `TH_RETAILER_PRODUCT_NAME`   | `retailer_product_id`, `lang`, `name`                                                                                                   | PK `(retailer_product_id, lang)`, SFC text-table pattern              |
| `TH_LISTING`                 | `retailer_product_id`, `catalogue_id`, `price`, `currency`, `active`                                                                    | PK `(retailer_product_id, catalogue_id)`, `IX (catalogue_id, active)` |
| `TH_CLASSIFICATION`          | `retailer_product_id` (PK), `classifier`, `archetype_product_id` (null = none), `input_hash`, `needs_review`, `resolved_at`, `reopened` | absent, stale hash or reopened = pending                              |
| `TH_CLASSIFICATION_ATTEMPT`  | (see below)                                                                                                                             |                                                                       |
| `TH_CATALOGUE_CHANGE_CURSOR` | `source`, `last_sequence`                                                                                                               | one row per Ingestion instance                                        |

#### TH_CLASSIFICATION_ATTEMPT columns

- `id`
- `retailer_product_id`
- `lang`
- `classifier`
- `classifier_version`
- `archetype_product_id`
- `input_hash`
- `created_at`
- `diagnostics`

### Schema: Ingestion (own changelog root)

Tables use the `IN_` prefix (Ingestion) instead of `TH_`. Constraint and index names follow the usual rules with the full
table name, e.g. `PK_IN_LISTING`.

| Table                                             | Columns (main)                                          | Notes  |
|---------------------------------------------------|---------------------------------------------------------|--------|
| `IN_RETAILER`, `IN_CATALOGUE`                     | as above, plus the adapter name and accepted languages  | seeded |
| `IN_INGESTION_RUN`                                | (see below)                                             |        |
| `IN_INGESTION_REJECT`                             | `ingestion_run_id`, `position`, `product_key`, `reason` |        |
| `IN_RETAILER_PRODUCT`, `IN_RETAILER_PRODUCT_NAME` | as HowiBuy, plus `removed`                              |        |
| `IN_LISTING`                                      | as HowiBuy, plus `last_seen_at`, `last_seen_run_id`     |        |

#### IN_INGESTION_RUN columns

- `id`
- `catalogue_id`
- `feed_type`
- `adapter`
- `adapter_version`
- `received_at`
- `started_at`
- `finished_at`
- `status`
- `file_path`
- `file_size`
- `file_sha256`
- counts - The following columns store counts:
  - `total`
  - `valid`
  - `rejected`
  - `new`
  - `changed`
  - `unchanged`
  - `missing` - the active Listings of the Catalogue that a Snapshot did not carry
  - `deactivated`
- `error`
- `replay_of_run_id` - the run whose stored Feed this run processes again

#### IN_INGESTION_RUN statuses

- `RECEIVED`
- `PROCESSING`
- `HELD`
- `DONE`
- `FAILED`
- `DISCARDED`

### Configuration

Ingestion: feed store directory, batch size, sanity-check share, eviction threshold, tombstone purge period. HowiBuy:
the LLM classifier version label. Catalogue Sync: both base URLs, the service-account credentials, page size,
classification rate, limit and page size.

## Testing Decisions

Tests check external behaviour. Real databases are used wherever the database is what's being tested (diffing,
deactivation, cursors, upserts, resolution). Keycloak-backed tests are kept to one happy path per deployable, because
Keycloak is slow.

1. **Adapters** — plain unit tests over small sample files per Retailer (well-formed, malformed records, a multi-MB file
   to check that parsing streams). No framework.
2. **Ingestion DB behaviour** — `@QuarkusTest` + Postgres only: snapshot → snapshot diff (new/changed/unchanged/missing),
   the hold on a large drop and its confirmation, Delta never deactivating, Deletion Feeds, reactivation without a new
   input hash, price-only change keeping the hash, the cursor's ordering and compaction, eviction and tombstones.
3. **HowiBuy DB behaviour** — `@QuarkusTest` + Postgres: applying a page (idempotent, cursor conflict `409`), pending
   detection, the resolution policy (precedence, agreement, disagreement → review), catalogue-aware candidate filtering,
   product selection, the three failure reasons.
4. **Pure logic** — Mockito + Weld: resolution policy, selection policy, the hash canonicalisation (same input, same
   hash, regardless of order), `SubstitutionSearch` with a stocked-archetype filter (framework-free module, plain JUnit).
5. **End to end** — one workflow per deployable with real Keycloak: retailer pushes a small Snapshot → run reaches
   `DONE`; sync applies it; an attempt is recorded for each product; a token exchange naming the Catalogue; an assessment
   returns real products.

## Out of Scope

- Pull-based Feeds (no Retailer needs them yet). The adapter contract leaves room for them.
- Raw-file retention (deferred in ADR 0006 until Retailers commit to a feed type). Files accumulate until then.
- Any UI for reviewing flagged Classifications or held runs. Endpoints only.
- Running the classifier automatically as products arrive (manual command first).
- The v15 recommendation algorithm (see Further Notes).
- Assessment for users without a Retailer token (the SPA).

## Further Notes

- **The algorithm v15 port adapts to this spec's wire shape.** The v15 plan also changes the outcome types (good enough
  / no alternative / recommended / no score) and wants the API to be a *list* of suggestions, "so retailers adapt
  once". The `AlternativeForProduct` redesign (issue 15) ships first and does not wait for v15, which is on hold. To
  keep v15 from breaking retailers a second time, the redesigned response is shaped so that a list of suggestions can
  be added to it without changing what is already there.
- Build order: schema + `CatalogueId` → HowiBuy storing/cursor endpoints → Ingestion receive/adapter/apply → Catalogue
  Sync → Classification endpoints and the `classify` command → tokens → assessment lookup and catalogue-aware
  recommendation. The old LLM-at-request-time path stays working until the lookup replaces it.
