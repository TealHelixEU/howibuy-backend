package eu.tealhelix.howibuy.v1.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder = ImmutableProductData.Builder.class)
public class ProductDataMixin {
}
