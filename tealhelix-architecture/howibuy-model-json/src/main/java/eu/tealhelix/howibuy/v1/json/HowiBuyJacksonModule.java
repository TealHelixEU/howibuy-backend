package eu.tealhelix.howibuy.v1.json;

import com.fasterxml.jackson.databind.module.SimpleModule;
import eu.tealhelix.common.types.RepresentableAsString;
import eu.tealhelix.howibuy.v1.model.ImmutableProductData;
import eu.tealhelix.howibuy.v1.model.ProductData;
import eu.tealhelix.howibuy.v1.model.ProductDataBuilderMixin;
import eu.tealhelix.howibuy.v1.model.ProductDataMixin;
import eu.tealhelix.howibuy.v1.types.ProductKey;
import eu.tealhelix.howibuy.v1.types.ProductKeyDeserializer;
import eu.tealhelix.howibuy.v1.types.RepresentableAsStringKeySerializer;
import eu.tealhelix.howibuy.v1.types.RepresentableAsStringSerializer;

public class HowiBuyJacksonModule extends SimpleModule {
	public HowiBuyJacksonModule() {
		// Let's keep them ordered alphabetically for sanity - but keep the ImmutableXxx.Builder mixins under the classes they build
		setMixInAnnotation(ProductData.class, ProductDataMixin.class);
		setMixInAnnotation(ImmutableProductData.Builder.class, ProductDataBuilderMixin.class);

		addDeserializer(ProductKey.class, new ProductKeyDeserializer());
		addSerializer(RepresentableAsString.class, new RepresentableAsStringSerializer());
		addKeySerializer(RepresentableAsString.class, new RepresentableAsStringKeySerializer());
	}
}
