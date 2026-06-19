package eu.tealhelix.howibuy.v1.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonPOJOBuilder(withPrefix = "")
@JsonIgnoreProperties("from") // Hint: use this when deserializing an type that extends another
public class ProductDataBuilderMixin {
}
