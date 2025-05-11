# Coding conventions

## General

1. TAB for indentation.
2. 2 TABs for line continuation.

## For pom.xml

### Dependencies

1. Group and sort dependencies as follows:
   - First is the classification of the dependency with the following order: FRAMEWORK dependencies, FRAMEWORK TEST,
     PROJECT (dependencies from within the project), PROJECT TEST (test-scoped dependencies from within the project),
     SPECS (specification APIs e.g. JAX-RS), OTHER (other non-test libraries), TEST (libraries intended for test scope).
   - Within each classification group sort alphabetically, first by Maven groupId, then by artifactId.
2. Do NOT define the scope of the dependencies in the `<dependencyManagement>` section of any pom.xml, define it when
   actually used. This allows us to have e.g., project-specific test modules that depend on JUnit in compile scope.
3. Define the version of dependencies in properties, even if used only in a single dependency. This makes it easy to
   upgrade dependencies with `mvn -N versions:display-property-updates`.

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
4. **INCOMPLETE** Name classes that are (more or less) full representations of a domain object to an external API with the `-Dto` suffix, e.g. `UserSearchResultDto`
    - **What is the difference between `-Param` and `-Dto`?**
        1. `-Param` is *input* to an API, when the data differ significantly from the domain object
        2. `-Dto` can be *both input and output*, both when there is no relevant domain object for the transferred data, or when the information we need to transfer closely matches the structure of the domain object.
5. Classes used as inputs/outputs of endpoints and are not part of the main model have the `-Request`/`-Response`
   suffix respectively.
6. Classes used as inputs/outputs of services and are not part of the main model have the `-Param`/`-Result`
   suffix respectively.

## Tests

1. Name the tested thing `sut` for "system under test"

## Communication endpoints

1. URLs for REST resources will follow the plural naming e.g. `/api/v1/customers`.
2. The resource class follows the same convention with the `-Resource` suffix, e.g. `CustomersResource`.

## Database artifacts

1. Names are `SNAKE_CASE`
2. Tables: capitals (even though it should not matter) like `TH_<MOD>_<NAME>`, where:
   - `<MOD>` is a 3-letter code of the module: `APL` for application, `COM` for common
   - `<NAME>` is the name of the domain object (not the entity, i.e. without the `-Entity` suffix)
   - `<NAME>` is *singular*
3. Columns: minuscules, no prefix or suffix
4. Primary key constraints: `PK_<TABLE>`, and `<TABLE>` is the full table name as specified above
5. Not null constraints: `NNL_<TABLE>__<COLUMN>`, the column name is capitalized
6. Unique constraints: `UNQ_<TABLE>__<COLUMN>`, the column name is capitalized
7. Foreign key columns: name them like `<target_table_without_prefix>_<target_column>`; minuscule as all columns
8. Foreign key constraints: `FK_<SRC_TABLE>__<SRC_COL>__<TARGET_TABLE>__<TARGET_COL>`; capitals and table names without prefixes
