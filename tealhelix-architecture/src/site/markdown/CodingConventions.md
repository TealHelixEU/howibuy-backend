# Coding conventions

## General

1. TAB for indentation.
2. 2 TABs for line continuation.

## For pom.xml

### Dependencies

1. Group and sort dependencies as follows:
   - First is the classification of the dependency with the following order: PROJECT (dependencies from within the
     project), FRAMEWORK dependencies, SPECS (specification APIs e.g. JAX-RS), OTHER (other non-test libraries),
     PROJECT TEST (test-scoped dependencies from within the project), FRAMEWORK TEST, TEST (libraries intended for test
     scope, like JUnit and Mockito).
   - Within each classification group sort alphabetically, first by Maven groupId, then by artifactId.
2. Do NOT define the scope of the dependencies in the `<dependencyManagement>` section of any pom.xml, define it when
   actually used. This allows us to have e.g., project-specific test modules that depend on JUnit in compile scope.
3. Define the version of dependencies in properties, even if used only in a single dependency. This makes it easy to
   upgrade dependencies with `mvn -N versions:display-property-updates`.
4. If a project (Maven module) uses artifacts from a dependency directly, it's preferable to declare the dependency
   directly rather than rely on transitive dependencies.
5. Mutiny (`io.smallrye.mutiny`) is exempt from the rule that keeps frameworks out of the non-deployable modules, and
   may be declared in any module. It is a datatype library rather than a framework — a handful of small jars over
   `java.util.concurrent.Flow`, with no CDI, no reflection and no container — so classify it under SPECS and reach for
   `Uni` and `Multi` without ceremony. The rule is really about CDI, JPA, JAX-RS, Hibernate and Quarkus itself.
   - The exemption removes a build obstacle; it is not an invitation to make the domain model asynchronous. A service
     interface returns a `Uni` because the work is genuinely asynchronous. A data type has no reason to, since
     asynchrony belongs to the code that talks to a database or the network.

## For Java code

1. Balance the branches in a method body: instead of `if (condition) { doSomething(); return x; } doSomethingElse(); return y;`,
   prefer `if (condition) { doSomething(); return x; } else { doSomethingElse(); return y; }`. Keep the `return` statement at the same depth in branches.
   - EXCEPTION: Fast-fail tests at the beginning of the method: `if (arg == null) return null; if (condition) ...`
2. Text in logging statements, exceptions:
   - Try to put variables at the end of the message so that when someone searches for a text in the logs, they will find exactly what they want. Examples:
     - GOOD: `log.info("User logged in: {}", username);`. Here I can search the code for `"User logged in:"` and find this code.
     - BAD: `log.info("User {} logged in", username);`. Here the constant strings are `"User "` and `" logged in"`. Both could be encountered in other places too, so searching for them does not uniquely identify the code that caused the log.
3. The usage of `var`: It is preferred when the type is obvious from the context. The type is obvious when creating a
   new object, e.g. `var foo = new Foo()` or for the return of a well-known Java method. Never replace `int` with `var`.
   Another case where the explicit type should be used instead of `var` is when the return type is parametric and the
   type is not obvious from the context. E.g. `var strings = List.of("a", "b", "c");` is legitimate. On the other hand,
   in `result = someService.findStuff().stream().toList()` the `result` is obviously a `java.util.List` because
   `Stream.toList()` is a well-known method, but the type parameter is not obvious.

### Javadocs

1. Separate paragraphs with a single `<p>` in its own line. No closing tag:
    ```java
    /**
     * Some content.
     * <p>
     * Content in a different paragraph.
     */
    ```

# Naming conventions

## Packages

1. All prefixed by the project prefix, `eu.tealhelix`. Do not use the default (unnamed) package!
2. Packages containing exposed APIs should have a version component following the project or module prefix,
   e.g. `eu.tealhelix.v1` or `eu.tealhelix.foo.v1` (see 4 below). The version package component should come after the
   package name of the Maven project (module) it is located. Other packages may follow the version component,
   e.g. `eu.tealhelix.foo.v1.model` or `eu.tealhelix.foo.v1.service.impl`.
3. Place common code i.e., code potentially useful to many modules, under a package name appropriate for the
   implemented functionality. E.g., classes under `eu.tealhelix.jpa` implement reusable, JPA-related functionality.
4. Each bounded context gets its own package under the project prefix (e.g. `eu.tealhelix.foo` or `eu.tealhelix.user`).
   Create appropriate sub-packages.

## Classes

**TBD/INCOMPLETE**

1. Name JPA entities after the domain with the `-Entity` suffix, e.g. `UserEntity`
2. **INCOMPLETE** Name the model interface after the domain, adding the suffix `-Data`, e.g. `UserData`. Use the `-Data` suffix for
   types that are input to business logic, even if they do not correspond to domain types.
3. **INCOMPLETE** Name concrete implementations of domain objects that act as arguments to the APIs after the domain with the `-Param`
   suffix, e.g. `UserParam`
4. **A data type crossing the JAX-RS boundary gets its own type; do not serialize a domain or service type directly.**
   This keeps the wire contract and the in-code model free to change independently, and keeps framework
   (Jackson) concerns out of the deliberately framework-free model. One exception: a small, flat, framework-free value
   type that is already exactly the wire shape and has no plausible divergence (an id value type, `Progress`) may be
   used on the wire directly instead of being copied.
5. Name a boundary type by whether it has an in-code twin:
    - If it is the wire representation of an in-code type — a core-model type, a service-interface `v1.types` type, or
      a services-model carrier — name it after that counterpart with the `-Dto` suffix, e.g. `CategoryDto` for
      `Category`, `CompassOverviewDto` for `CompassOverview`. (`-Dto` names the pattern rather than the domain; it is
      used here deliberately, because the suffix communicates that a corresponding in-code type exists to read.)
    - If it is a wire-only shape with no such twin — a request/response envelope, an error body — name it
      `<Thing>Request` / `<Thing>Response`, e.g. `AnswerRequest`, `NextQuestionResponse`, `StabilityWindowResponse`.
    - Accept two consequences knowingly: (a) the suffix mixes axes — `-Request`/`-Response` name a type by its HTTP
      *direction*, while `-Dto` names it by *kind*, so a `-Dto` name does not tell you request-vs-response (which is in
      fact more honest for a twin sent in both directions); (b) the name is relative to the model, so introducing or
      removing an in-code twin flips a type between `-Dto` and `-Response`.
6. **TBD or phased out** Classes used as inputs/outputs of services and are not part of the main model have the `-Param`/`-Result`
   suffix respectively.
7. When a word component of a Java identifier is all capitals, de-capitalize it, e.g. "DTO" &rarr; "SomethingDto", "DAO" &rarr; "SomethingDao".
8. Clearly distinguish classes that hold data from ones that act on data. The former should be named after the thing they model. The latter should be given names that resemble proper nouns, marking them as programmatic "personas" that act on data. Use the `-Service`/`-ServiceImpl` suffix appended to the name of a "thing" (the former case) to mark something that acts on that thing.

## Tests

1. Name the tested thing `sut` for "system under test"

## Communication endpoints

1. URLs for REST resources will follow the plural naming e.g. `/api/v1/customers`.
2. The resource class follows the same convention with the `-Resource` suffix, e.g. `CustomersResource`.

## Database artifacts

1. Names are `SNAKE_CASE`
2. Tables: capitals (even though it should not matter) like `<PREFIX>_<NAME>`, where:
   - `<PREFIX>` identifies the database the table lives in, one per deployable that owns a database:
     - `TH` (TealHelix) for the HowiBuy database, shared by every bounded context deployed in `howibuy`
     - `IN` (Ingestion) for the Ingestion database
   - `<NAME>` is the name of the domain object (not the entity, i.e., without the `-Entity` suffix)
   - `<NAME>` is *singular*
   - E.g., `TH_USER`: A table for users of HowiBuy
3. Columns: minuscules, no prefix or suffix
4. Primary key constraints: `PK_<TABLE>`, and `<TABLE>` is the full table name as specified above
5. Not null constraints: `NL_<TABLE>__<COLUMN>`, the column name is capitalized
6. Unique constraints: `UQ_<TABLE>__<COLUMN>`, the column name is capitalized
7. Foreign key columns: name them like `<target_table_without_prefix>_<target_column>`; minuscule as all columns
8. Foreign key constraints: `FK_<SRC_TABLE>__<SRC_COL>__<TARGET_TABLE>__<TARGET_COL>`; capitals and table names without prefixes
9. Join tables: Name the tables like `<SRC_TABLE>_<RELATION_NAME>`, e.g. for a relation between `PlayerEntity` objects called `friendsOrFoes`, the join table name would be `TH_PLAYER_FRIENDS_OR_FOES`.
   - Use the "Foreign key columns" naming convention for the join table columns.
   - In the special case where the two ends of the relation are the same entity, use the "Foreign key columns" convention for the column refering to the logical master of the relation and the name of the relation property converted to singular, e.g. `player_id` and `friend_or_foe`.
10. Indexes: `IX_<TABLE>__<COLUMN>`
11. PostgreSQL truncates identifiers longer than 63 characters (see the [documentation](https://www.postgresql.org/docs/current/sql-syntax-lexical.html#SQL-SYNTAX-IDENTIFIERS)).
    When a foreign key constraint name to a primary key would exceed it, leave out the trailing `__<TARGET_COL>`, e.g.
    `FK_RETAILER_PRODUCT_NAME__RETAILER_PRODUCT_ID__RETAILER_PRODUCT`.

**TODO:** How to shorten the other names that exceed the limit of PostgreSQL.

## DAOs

1. Place methods to DAOs according to the returned type. If it returns `SomethingEntity` or `Collection<SomethingEntity`, it belongs to the `SomethingDao`.
2. Never name methods that access the DB with a `get-` prefix. They look like ordinary getters.
   - Use `find-` or `findBy-` if the method may return `null`/empty `Optional`
   - Use `require-` or `requireBy-` if the method throws when it does not find a result
   - Use `retrieve-` if the method performs some extra operation, e.g. aggregation of results
