# Sustainable Food Compass (SFC)

Status: ready-for-agent

Domain vocabulary follows the project glossary (`tealhelix-architecture/src/site/markdown/CONTEXT.md`).
Architecture decisions are recorded in ADRs `0001`–`0004` under
`tealhelix-architecture/src/site/markdown/adr/`; this spec must be implemented consistently with them.

## Problem Statement

As a platform user, I care about different facets of food sustainability to different degrees, but
today there is no way for me to tell the platform where I stand. Without that, the platform's product
assessment can't reflect what matters to me. I need a guided questionnaire I can take in my own
language, at my own pace — pausing, resuming, and revising as I go — that records a stable snapshot of
my attitudes and lets me re-take it over time as my views change.

## Solution

The **Sustainable Food Compass** — a fixed, multilingual questionnaire organised into **categories**,
each addressing one **sustainability dimension** (Ecological, Social, Economic, Health, Animal
welfare). Every **question** is answered on the same fixed 5-point Likert scale. A user works through
categories in any order, is guided to the next unanswered question within a category, and has each
**answer** saved the instant it is made, so they can pause and resume freely and revisit or change
answers at will. Progress (overall and per-category) and an estimated completion time are shown
throughout.

When the user has answered everything, they explicitly mark the compass complete; the system verifies
completeness and freezes the round as an immutable **attempt** — the stable record consumed by the
project's external scientific model. After a configured **stability window** elapses, the user may
start a fresh, blank attempt, building a history of attempts over time. Content is served in the
language the user requests, with a configured default.

## User Stories

1. As a user, I want to see all sustainability categories with their names and rich descriptions, so I understand what the compass covers before I start.
2. As a user, I want each category's video link and its link to a detailed description, so I have deeper context when I need it.
3. As a user, I want the whole questionnaire presented in my own language, so I can understand and answer accurately.
4. As a user who doesn't specify a language, I want a sensible default language, so the compass is still usable.
5. As a user who requests an unsupported language, I want a clear error rather than partially-translated content, so I'm never misled by missing text.
6. As a user, I want to see the estimated time to complete the whole compass, so I can decide whether to start now.
7. As a user, I want to see a per-category time estimate, so I can budget time for a single category.
8. As a user, I want to see my overall progress as answered/total and a percentage, so I know how far I have to go.
9. As a user, I want to see per-category progress, so I know which categories still need work.
10. As a user, I want to work through categories in any order I choose, so I can start with what matters most to me.
11. As a user, I want to fetch all the questions in a category together with my current answers, so I can review a category as a whole.
12. As a user, I want to fetch all questions across all categories with my answers, so I can review everything at once.
13. As a user, I want to answer a question by picking one option on a 5-point scale, so I can express the strength of my view.
14. As a user, I want the five scale options labelled in my language, so I understand what each point means.
15. As a user, I want my answer persisted the moment I make it, so I never lose progress.
16. As a user, I want to pause and resume later exactly where I left off, so I can complete the compass across several sessions.
17. As a user, I want to ask for the next question in a category and be given the first one I haven't answered, so I can move forward without deciding what's next.
18. As a user, I want to be prevented from skipping ahead past unanswered questions, so I complete a category in order.
19. As a user, I want to be told when I have answered the last remaining question in a category, so I know to move on.
20. As a user, I want reaching the end of a category to leave me at that category (not jump me into another), so navigation between categories stays my choice.
21. As a user, I want to go back and revisit any question I've already answered, in any order, so I can review my responses.
22. As a user, I want to change a previously given answer while my attempt is still open, so I can correct or update my view.
23. As a user, I want to explicitly mark the compass as complete when I'm done, so completion is a deliberate act and never happens by accident.
24. As a user, when I try to complete but haven't answered everything, I want to be told exactly which questions are still unanswered, so I can finish them.
25. As a user, I want completion refused unless every question is answered, so my committed record is meaningful.
26. As a user, once I complete an attempt, I want it kept as an unchangeable record, so my committed answers stay stable.
27. As a user within the stability window after completing, I want to be prevented from starting a new attempt and told when I'll be able to, so my previous record remains stable for as long as required.
28. As a user, once the stability window has elapsed, I want to start a fresh attempt, so I can re-express attitudes that have changed.
29. As a user, I want a new attempt to start blank, so each round reflects my current views independently rather than copying forward stale answers.
30. As a user, I want at most one attempt in progress at a time, so my current answers are never ambiguous.
31. As the product-assessment capability, I want to read a user's committed compass answers, so assessments can reflect the user's sustainability priorities.
32. As the external scientific model, I want a completed attempt's answers to remain stable for a configured period, so computations over them stay valid.
33. As an operator, I want to configure the stability-window duration, so I can tune how often users re-take the compass.
34. As an operator, I want to configure the supported languages and the default language, so I control which locales are offered.
35. As an operator, I want to configure the estimated seconds-per-question, so the displayed completion estimate is accurate.

## Implementation Decisions

**Placement & modules** (ADR 0001)
- SFC is a bounded context (`eu.tealhelix.sfc.*`) deployed inside the existing `howibuy` Quarkus application, sharing its datasource and Keycloak client — not a separate microservice.
- It lives in dedicated Maven modules mirroring the HowiBuy layering: `sfc-model` (framework-free domain types, in `tealhelix-architecture`), `sfc-dao`, `sfc-dao-hibernate-reactive`, `sfc-service-interfaces`, `sfc-services`, `sfc-jaxrs`. `sfc-services-model` is created only if a DAO↔service carrier type actually appears.
- Because Quarkus runs one Liquibase changelog per datasource, the aggregating root `db.changelog.xml` moves to the `howibuy` deployable module and includes both HowiBuy's and SFC's per-module changelogs. Each module owns only its own changesets. SFC's changesets follow the existing naming conventions and seed fixed content via `loadData` from CSV under `context="appdata"` (base CSV per table, one text CSV per language, mirroring the food-term precedent).

**Domain & schema**
- `SustainabilityDimension` is a fixed Java enum of five values, persisted as a string. A `Category` carries a `dimension` attribute; there is no unique constraint on `dimension` (today one category per dimension, but the schema allows several per dimension without migration).
- The Likert scale is a fixed enum (ordinals 1–5). There are no scale tables; an `Answer` stores the raw ordinal as `SMALLINT` with a `CHECK 1..5`. The five localized labels come from a resource bundle keyed by the enum and are resolved server-side, returned alongside the localized content (ADR 0002).
- Tables (constraint/index/column naming per the coding conventions):
  - `TH_SFC_CATEGORY` — `id`, `dimension`. Deliberately thin (all human-facing fields are localized).
  - `TH_SFC_CATEGORY_TEXT` — `(category_id, lang)` unique; `name`, `description` (rich text → `TEXT`), `video_url`, `detail_url`. Links are treated as localized.
  - `TH_SFC_QUESTION` — `id`, `category_id` (FK), `position`; unique `(category_id, position)`.
  - `TH_SFC_QUESTION_TEXT` — `(question_id, lang)` unique; `text`.
  - `TH_SFC_ATTEMPT` — `id`, `user_id`, `status` (`IN_PROGRESS`/`COMPLETED`), `completed_at`. Partial unique index on `user_id WHERE status = 'IN_PROGRESS'` enforces at most one in-progress attempt per user.
  - `TH_SFC_ANSWER` — composite key `(attempt_id, question_id)`; `value SMALLINT CHECK 1..5`.
- **User is referenced by ID only** (ADR 0001): `TH_SFC_ATTEMPT.user_id` is a raw `UUID` with a DB-level FK to `TH_USER_PROFILE(id)` and **no** JPA association, so `sfc-dao-hibernate-reactive` stays free of HowiBuy persistence code. The authenticated `User` supplies the id. This deliberately diverges from `ConsentEntity`'s `@ManyToOne`.

**Internationalization** (ADR 0002)
- Per-entity translation tables (base + `*_TEXT`). Requested language is an explicit `?lang=xx` query param on read endpoints, defaulting to a configured default. Supported languages are a configured closed set; an unsupported language returns 400; there is no silent per-field fallback. Translation completeness is guaranteed by the content authors (no runtime check, no automated test). Content lives in DB translation tables; the fixed scale labels (chrome) live in a resource bundle.

**Attempt lifecycle** (ADR 0003)
- Two states, `IN_PROGRESS → COMPLETED`. Answers are freely upsertable while `IN_PROGRESS` (pause/continue + revisit). Completion is strictly explicit; it validates that every question in every category is answered, and on failure rejects with the unanswered question IDs (422). On success it stamps `completed_at` and freezes the attempt immutably. A completed attempt is never re-opened.
- Eligibility to start a new attempt is `completed_at + stabilityWindow`, computed on read (no scheduler). A new attempt starts blank. `completed_at` and the eligibility comparison use the existing `DateTimeService` (extend it if an `Instant`/`LocalDateTime` accessor is needed) so time is controllable in tests.

**Answering & the eligibility guard**
- Answering upserts a single answer for `(current attempt, question)` and persists immediately. The `IN_PROGRESS` attempt is created lazily on the first answer **only if the user is eligible**. If the user is inside a prior attempt's stability window and has no in-progress attempt, answering is rejected with 409 (do not silently create an attempt).

**Navigation** (ADR 0004)
- Categories are independent and unordered; only questions within a category are ordered. "Next question" is category-scoped and returns the first unanswered question in position order — which enforces "all previous answered" by construction. When none remain it returns a "category complete" signal and does not advance into another category. Revisiting (fetching a category's questions with current answers, in any order) is unrestricted. Note: if a user revisits and changes an already-answered question, "next" still routes to the earliest remaining unanswered question — a deliberate, cheap-to-change default.

**Progress & estimate**
- Progress is computed on read against the current attempt, exposed both overall (`answered/total`) and per-category. Estimated completion time is `sfc.seconds-per-question × question count`, exposed overall and per-category.

**Configuration**
- `sfc.languages` (closed supported set), `sfc.default-language`, `sfc.stability-window` (`Duration`), `sfc.seconds-per-question`. Static Quarkus config; changing them needs a redeploy.

**API contract (intended surface, derived from the decisions)**
- Read endpoints take `?lang=xx`. Paths follow the existing plural `/api/v1/…` convention under an `sfc` segment; exact request/response DTOs are for the implementer to finalise following existing patterns (records colocated with the resource; `User` obtained from the security context). The surface is:
  - An **overview** read: categories (localized name/description/links), overall + per-category progress and estimate, the localized scale labels, current attempt status, and whether the user is eligible to start a new attempt.
  - A **category questions** read: a category's questions (localized) with the user's current answers.
  - An **all questions** read: every question (localized) with the user's answers.
  - A **next-question** read for a category: the frontier question or a category-complete signal.
  - An **answer upsert**: set/replace the answer for one question on the current attempt (immediate save; lazy eligible attempt creation; 409 when ineligible).
  - A **completion** action on the current attempt: validate-all-answered then lock (422 + unanswered IDs on failure).
- Endpoints require an authenticated end-user (one with a `TH_USER_PROFILE` row). This surface was not exhaustively grilled; the implementer may refine paths/shapes within these decisions.

## Testing Decisions

A good test here exercises **external behavior** — HTTP status and body, and state observable back through the API (e.g. an answer persists, a completed attempt can't be mutated, an ineligible user is refused) — never internal structure. Given the DB interactions are non-trivial (immutability, the partial-unique in-progress constraint, composite-key answers, translation joins, time-based eligibility), real-DB accuracy is preferred over mocking wherever the DB is the point.

Because launching Keycloak in a test context is **very slow**, the seams are tiered:

1. **One constrained end-to-end test** — `@QuarkusTest` + `PostgresAndKeycloakTestResource`, using a real Keycloak-issued token (`@InjectKeycloak`) — exercising only the **main happy-path workflow**: fetch localized categories/questions → answer through a category via next-question → complete → confirm the attempt is locked. Prior art: `CorrelationIdWorkflowTest`. Keep it lean; it is not where edge cases are covered.
2. **DB-detail tests** — `@QuarkusTest` with the Postgres-only `PostgresTestResource` (no Keycloak), driving the service/DAO layer for the DB-nuanced behaviors: completed-attempt immutability, the one-in-progress-per-user constraint, completion validation (422 + unanswered IDs and the success path), the stability-window eligibility and the new-blank-attempt flow (with `DateTimeService` controlled), progress/estimate counts, translation joins and the 400-on-unsupported-language, and the 409 answer-while-ineligible guard.
3. **Pure-logic unit tests** — Mockito + Weld (`@EnableAutoWeld`/`@AddBeanClasses`) with `MockReactivePersistenceContextFactory` and a stubbed `DateTimeService`, for genuinely DB-independent logic (frontier selection, estimate arithmetic, eligibility comparison). Prior art: `UserImpersonationServiceImplTest`, `ProductAssessmentServiceImplTest`, `FoodTermGlossaryTest`.

`DateTimeService` is the time seam — stub it to make stability-window eligibility deterministic without sleeping. There is no automated translation-completeness test (completeness is guaranteed by the content authors).

## Out of Scope

- Consumption of committed attempts by product assessment and by the external scientific model — SFC only produces and stores the attempts; the reading/integration is a separate effort.
- Any scoring or weighting of the 1–5 answers into a derived value.
- Any UI or API for editing/authoring questions and categories — content is fixed and seeded.
- The content behind the "detailed description" link, and the video itself — only the (localized) links are stored and served.
- A persisted per-user language preference — language is a per-request param; a stored preference is a separate feature that could later supply the default.
- Runtime or automated enforcement of translation/label completeness.
- A rich history-browsing surface over previous completed attempts — storing the history is in scope; exposing anything beyond the current attempt state and start-new-attempt eligibility is not part of this spec.
- The frontend/client implementation.

## Further Notes

- Respect ADRs `0001` (placement/modules/user-by-ID), `0002` (i18n + content-vs-chrome split), `0003` (attempt lifecycle), `0004` (navigation), and the glossary, throughout.
- The rich-text `description` column is a net-new `TEXT`-class column for this codebase (no such column exists today). Content is fixed and curated, so no HTML sanitization layer is planned; if the client renders it as HTML, treat it as trusted content.
- Category-per-dimension is 1:1 today, but the absence of a unique constraint on `dimension` intentionally leaves room for several categories per dimension later.
- Build order should follow the module dependency chain (`sfc-model` → `sfc-dao` → `sfc-dao-hibernate-reactive` → `sfc-service-interfaces` → `sfc-services` → `sfc-jaxrs` → wire into the `howibuy` deployable), ideally test-first.
