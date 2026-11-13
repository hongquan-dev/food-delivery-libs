package com.fooddelivery.apiflow.validation;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonSchemaUtilTest {

    @Test
    void loadSchemaAcceptsSupportedAndAnnotationKeywords() throws IOException {
        JsonNode schema = JsonSchemaUtil.loadSchema("schemas/supported.json");

        assertThat(JsonSchemaUtil.validate(schema, "{\"foodId\": 17290500003007, \"quantity\": 2, \"notes\": null}"))
                .isTrue();
        assertThat(JsonSchemaUtil.validate(schema, "{\"foodId\": 1, \"quantity\": 2, \"extra\": true}")).isFalse();
        assertThat(JsonSchemaUtil.validate(schema, "{\"foodId\": 1, \"quantity\": 1.5}")).isFalse();
        assertThat(JsonSchemaUtil.validate(schema, "{\"foodId\": 1, \"quantity\": 2, \"toppings\": [1]}")).isFalse();
    }

    // Every problem of the file is reported at once, with its location
    @Test
    void loadSchemaRejectsUnsupportedKeywordsAndTypes() {
        assertThatThrownBy(() -> JsonSchemaUtil.loadSchema("schemas/unsupported.json"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("#: additionalProperties must be true or false")
                .hasMessageContaining("#/properties/username: unsupported keyword 'minLength'")
                .hasMessageContaining("#/properties/tags/items: unsupported keyword 'enum'")
                .hasMessageContaining("#/properties/tags/items: unknown type \"text\"");
    }
}
