package eu.tealhelix.howibuy.sys;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.tealhelix.common.test.quarkus.PostgresTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * The application's managed JSON mapper renders an {@link Optional} as its contained value, or {@code null} when empty —
 * i.e. the jdk8 datatype module is registered — so response types may carry {@code Optional} fields without the wire
 * shape degrading to Jackson's bean rendering of {@code Optional}.
 */
@QuarkusTest
@WithTestResource(value = PostgresTestResource.class, initArgs = @ResourceArg(name = "contexts", value = "appdata"))
public class ManagedObjectMapperTest {
	@Inject
	ObjectMapper objectMapper;

	@Test
	void serializesOptionalAsItsValueOrNull() throws Exception {
		assertEquals("\"present\"", objectMapper.writeValueAsString(Optional.of("present")), "a present Optional serializes as its value");
		assertEquals("null", objectMapper.writeValueAsString(Optional.empty()), "an empty Optional serializes as null");
	}
}
