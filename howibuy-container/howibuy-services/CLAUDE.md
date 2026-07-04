# howibuy-services — Claude notes

Service implementations for the HowiBuy microservice. This file covers conventions that aren't obvious from the code.

## AI service facades

An `*AiService` interface (annotated `@RegisterAiService`) is the raw Quarkus Langchain4J binding: its methods take
already-rendered `String` template parameters and call the model **synchronously**. Business logic never talks to it
directly — it goes through a paired `*AiFacade`, which owns three jobs:

1. **Sync → Mutiny.** The facade returns `Uni<T>`, not the AI service's plain `T`. It wraps the blocking call with
   `Uni.createFrom().item(...).runSubscriptionOn(Infrastructure.getDefaultExecutor())` and re-emits on the caller's
   Vert.x context. Pattern reference: DietWise `RecipeExtractionAiFacadeImpl`.
2. **Circuit breaker.** The blocking call is guarded so an Ollama outage degrades gracefully instead of stalling.
3. **Model → template rendering.** The facade translates the application's domain model into the flat `String`s the
   template expects (e.g. a `Map`/`List` rendered as a markdown section with heading + bullets, omitted when empty).
   This formatting lives in the facade, not in the `.md` template — Langchain4J templating is too limited for it.

### Signature conventions

- **Pass the domain aggregate whole, don't destructure it.** `extractL1Category` takes `ProductData`, not its
  individual fields. This keeps `language`/`name`/`characteristics`/`tags` sharing one source of truth (the locale is
  `ProductData.getLanguage()`, so a separate `Locale` param could drift from the rest), keeps the template's parameter
  shape out of every call site, and lets a new template field change only the facade impl.
- **Never leak persistence entities into the facade.** Candidate categories arrive as `List<String>` (names), not
  `List<ArchetypeCategoryEntity>` — the entity is a JPA type from the DAO impl module and must not cross into the
  service/AI layer. The caller flattens (`entities.stream().map(...::getName).toList()`) before calling.
- **Keep the shape uniform across the L1/L2/L3 family.** As sibling `extractL2Category`/`extractL3Category` methods are
  added for the SAFAD taxonomy levels, each takes `(ProductData, List<String> candidateNames)` and returns
  `Uni<String>`.
