package eu.tealhelix.sfc.tests;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import eu.tealhelix.common.test.quarkus.PostgresTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.common.WithTestResource;

/**
 * A Postgres-only test resource seeded from the {@code appdata} Liquibase context, shared by the compass integration
 * tests. Under the default {@link WithTestResource} scope ({@code MATCHING_RESOURCES}), every test carrying this
 * annotation is grouped onto a single Quarkus instance, so the database starts once for the whole group and no
 * unrelated resource (e.g. Keycloak) leaks into these tests.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@WithTestResource(value = PostgresTestResource.class, initArgs = {
		@ResourceArg(name = "contexts", value = "appdata")
})
public @interface WithCompassDb {
}
