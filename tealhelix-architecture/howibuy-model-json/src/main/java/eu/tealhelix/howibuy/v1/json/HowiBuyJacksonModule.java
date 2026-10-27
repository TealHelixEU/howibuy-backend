package eu.tealhelix.howibuy.v1.json;

import com.fasterxml.jackson.databind.module.SimpleModule;
import eu.tealhelix.common.types.RepresentableAsString;
import eu.tealhelix.common.types.json.RepresentableAsStringKeySerializer;
import eu.tealhelix.common.types.json.RepresentableAsStringSerializer;

public class HowiBuyJacksonModule extends SimpleModule {
	public HowiBuyJacksonModule() {
		// Let's keep them ordered alphabetically for sanity - but keep the ImmutableXxx.Builder mixins under the classes they build
		addSerializer(RepresentableAsString.class, new RepresentableAsStringSerializer());
		addKeySerializer(RepresentableAsString.class, new RepresentableAsStringKeySerializer());
	}
}
