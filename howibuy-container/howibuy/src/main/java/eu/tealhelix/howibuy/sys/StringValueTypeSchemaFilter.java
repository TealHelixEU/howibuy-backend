package eu.tealhelix.howibuy.sys;

import java.util.Set;

import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.media.Schema.SchemaType;

/**
 * Several value types serialize as a bare string on the wire but smallrye-openapi, which introspects the Java type
 * rather than the Jackson serializer, would emit them as objects:
 * <ul>
 *     <li>{@code CategoryId}, {@code QuestionId}, {@code ProductKey} — {@code RepresentableAsString} ids rendered by a
 *     Jackson serializer, otherwise introspected as a recursive object;</li>
 *     <li>{@code Locale}, {@code Currency} — JDK value types Jackson renders as a language tag / currency code,
 *     otherwise expanded into their full bean shape.</li>
 * </ul>
 * This filter rewrites those component schemas to a plain string, matching the wire.
 */
public class StringValueTypeSchemaFilter implements OASFilter {
	private static final Set<String> STRING_VALUE_TYPES = Set.of("CategoryId", "QuestionId", "ProductKey", "Locale", "Currency");

	@Override
	public void filterOpenAPI(OpenAPI openAPI) {
		var components = openAPI.getComponents();
		if (components == null || components.getSchemas() == null) return;
		for (var name : STRING_VALUE_TYPES) {
			if (components.getSchemas().containsKey(name)) components.addSchema(name, OASFactory.createSchema().addType(SchemaType.STRING));
		}
	}
}
