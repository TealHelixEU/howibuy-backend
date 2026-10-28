# Retailer Catalogues are ingested by a separate deployable and classified offline; assessment only looks products up

Assessment no longer interprets a scanned product with the LLM at request time. Each partner
Retailer's product data is ingested ahead of time, every Retailer Product is classified offline to
at most one Archetype Product, and assessment becomes a lookup of the scanned product's
Classification. A product without one is reported as unidentified and gets no assessment.
Retailers need fast responses. Online LLM classification is too slow already, and getting the
accuracy we need may take bigger, slower models, so adding infrastructure would not fix the delay.

The work is split into three parts with narrow contracts between them:

1. **Ingestion**, a separate deployable with its own database (a schema and login that HowiBuy cannot
   read). It receives a Retailer's Feeds
   through a per-Retailer adapter: push or pull, in whatever format the Retailer uses. It stores
   the raw file and answers `202` with a status endpoint of the Feed and its Ingestion Runs. Afterwards it
   stream-parses and validates the file, and works out which Listings are new, changed, inactive or
   removed. Every Retailer Product that changes takes the next number of one change sequence, so
   the changes can be read in order from a cursor.
2. **Catalogue Sync**, an operator command and the only bridge from Ingestion to HowiBuy. It reads
   the changes after HowiBuy's cursor and forwards them to HowiBuy: prices, inactivations and
   removals included. It never classifies, so a full Catalogue is visible, with current prices,
   long before the classifier gets through it. At first it is run by hand.
3. **HowiBuy** keeps the Retailer Products, Listings and Classifications it is given, and serves
   assessments from them. A product whose classification inputs changed becomes pending. HowiBuy
   exposes the identification half of today's LLM pipeline as a "classify this product"
   operation, so the LLM pipeline keeps living next to the taxonomy, glossary and guidance it
   reads. A separate `classify` command of the Catalogue Sync tool works through the pending
   products at a pace the operator controls. Experts and people record their Classifications through
   HowiBuy too.

## Considered Options

### Where ingestion runs

- **A separate deployable with its own database (chosen).** Retailer files are large and come from
  outside, and an adapter bug or a malformed file must never take down assessment. This differs
  on purpose from ADR 0001, which kept SFC inside `howibuy`. SFC shares data with assessment.
  Ingestion shares nothing with it except the changes it exposes, and those are its only contract.
- **A module inside `howibuy`.** Cheaper, but ingestion's memory and CPU spikes and its failures
  would hit the service that retailers are waiting on.

### How changes reach HowiBuy

- **A change sequence on the product row, read from a cursor (chosen).** Each change is the
  complete current state of a Retailer Product, so applying it is idempotent and a product changed
  several times between two reads arrives once. Re-reading is harmless, and HowiBuy keeps the
  cursor in the same transaction as the products it applies. Resetting the cursor re-sends
  everything. Postgres holds this easily at our scale.
- **A separate log table.** It would keep every intermediate change, which nothing needs, and grow
  without bound.
- **A message broker.** Extra infrastructure that this volume does not justify.
- **A queue that consumes messages.** Once a message is consumed it is gone, so it cannot be
  replayed.

### When products are classified

- **Separately from syncing, from the products HowiBuy marks pending (chosen).** Syncing is fast
  and classifying is slow, so coupling them would hold prices and inactivations back behind the
  LLM. Re-classification after the classifier improves marks as pending the products classified
  by a given classifier version, instead of replaying the changes.
- **While syncing, for each change whose inputs changed.** Simpler to drive, but a new Catalogue
  would only appear once all of it was classified.

### What is classified

- **One Archetype Product per Retailer Product (chosen).** The archetype is a property of the
  physical product. Classification Attempts are made per language, and the product's single
  Classification is resolved from them. Attempts that disagree are flagged for review, and resolve
  to the attempt in the Retailer's classification language.
- **One classification per language or per Catalogue.** Rejected: the same product could then
  get different Alternatives in different countries.

### Unclassified products

- **No assessment (chosen).** It keeps the response time predictable.
- **Fall back to online LLM classification.** Rejected for the latency reason above.

## Consequences

- Assessment requires a Catalogue. The impersonation token HowiBuy mints carries the user and the
  Catalogue. The Catalogue belongs to the session, not to the user, so it is read from the
  token's claims in the JAX-RS layer and handed to the services as a parameter. Users who log in
  directly (the SPA) get no assessments.
- Callers are told apart by Keycloak realm roles (Retailer, Catalogue Sync, operator), not by
  whether they are a service account. Catalogue Sync has a Keycloak client of its own, used
  against both Ingestion and HowiBuy.
- `SingleProductAssessor` splits into identification (finding the Reference Product) and
  recommendation. Online identification becomes the Classification lookup. The LLM descent moves
  behind the "classify this product" operation, and behind an operator-only preview of it that
  classifies posted product data without storing anything, so the classifier can still be
  evaluated against data that is not a Catalogue.
- `ProductAssessmentOutcome` gets a reason next to its type. `FAILURE_TO_IDENTIFY` is *unknown
  product* (HowiBuy has no such Retailer Product), *not classified* (known but still pending) or
  *no matching archetype* (the classifier ran and found none). HowiBuy can tell the first two apart
  because it stores products before they are classified. Classifier results of "no matching
  archetype" are stored, together with the classifier version, so they can be retried
  selectively. Malformed classifier replies are not stored, so those products stay pending.
- An Alternative is a real Retailer Product, and it only comes from active Listings in the user's
  Catalogue. `AlternativeForProduct` is redefined to carry the Retailer Product's data, plus its
  archetype for completeness and diagnostics. This breaks the API, which is acceptable because no
  client depends on it yet. The redefinition does not wait for the v15 recommendation algorithm,
  which adapts to it, so it is shaped to take a list of suggestions later without breaking again.
  Whether a Catalogue stocks an archetype filters the candidates *before* the best archetype is
  chosen. Choosing which Retailer Product of that archetype to offer is a separate, swappable
  policy, starting with the lowest price. When the best archetype is the Reference Product
  itself, the Alternative is `GOOD_ENOUGH` and names no Retailer Product. The scanned product is
  the implied answer, so no Listing is needed, even if it is Inactive.
- Names are per language; characteristics and tags are not. Each Catalogue has a primary
  language, every product it lists must be named in it, and names fall back to it.
- Being Inactive stops a Listing from being recommended, not from being assessed. A user scanning
  a product that dropped out of the latest Snapshot still gets an assessment.
- Snapshot Feeds make missing Listings inactive. Delta Feeds never do. Apart from Snapshots, only
  an explicit Deletion Feed does. An inactive Listing keeps its Classification, so a product coming back (e.g. a
  seasonal one) is not classified again. It is removed for good only when its Last Seen is older
  than a threshold, and Ingestion decides that. A Snapshot that drops an unusually large share of
  a Catalogue is held for confirmation instead of applied.
- The hash that decides whether a product is classified again covers its names,
  characteristics and tags, and never its price. It may need more inputs later.
- A Feed is for one Catalogue, or for every active Catalogue of its Retailer when the Retailer
  sends one file for all of them. The database decides which Catalogues take part, not the file.
  Such a Feed is stored once and gets one Ingestion Run per Catalogue, and everything after the
  receipt is per Catalogue, as for any other Feed.
- Every Feed is recorded: Retailer, feed type, receipt time, raw file location, size and checksum.
  Every Ingestion Run is recorded: Feed, Catalogue, adapter version, status, timings, counts
  (total, valid, rejected, new, changed, unchanged, inactivated, removed) and the rejected records
  with their reasons.
  Classification has no run record of its own: every Classification Attempt records its
  classifier, classifier version, the input hash it was made from and its time.
- Raw files are deleted after a retention period. The policy depends on the feed type and is
  deferred until the Retailers commit to their formats. A file is never deleted while its run is
  unfinished or failed.
