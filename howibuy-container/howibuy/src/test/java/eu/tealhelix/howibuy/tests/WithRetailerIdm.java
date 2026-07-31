package eu.tealhelix.howibuy.tests;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import eu.tealhelix.common.test.quarkus.PostgresAndKeycloakTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.common.WithTestResource;

/**
 * What a test needs to act as a retailer: this application's database and the IDM that authenticates retailers. Under
 * the default {@link WithTestResource} scope ({@code MATCHING_RESOURCES}), every test carrying this annotation is
 * grouped onto a single Quarkus instance, so the containers start once for the whole group.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@WithTestResource(value = PostgresAndKeycloakTestResource.class, initArgs = {
		@ResourceArg(name = "pgContexts", value = "dev"),
		@ResourceArg(name = "pgConnectionDbUser", value = "th_howibuy"),
		@ResourceArg(name = "pgConnectionDbPassword", value = "th_howibuy"),
})
public @interface WithRetailerIdm {
}
