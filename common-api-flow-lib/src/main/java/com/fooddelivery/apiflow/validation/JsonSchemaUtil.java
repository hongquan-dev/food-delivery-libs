package com.fooddelivery.apiflow.validation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;

/**
 * Minimal JSON Schema checker for request bodies: structure and data types only; value rules belong in the route's
 * validator. Supports {@code type}, {@code required}, {@code properties}, {@code items} and
 * {@code additionalProperties}; other keywords are ignored.
 *
 * <p>Unlike the standard, {@code additionalProperties} defaults to {@code false} (unknown fields are rejected at
 * every level) and an unknown {@code type} name never matches.
 */
public final class JsonSchemaUtil {
    // FAIL_ON_TRAILING_TOKENS: reject bodies such as {"a":1}garbage instead of reading only the first value
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private JsonSchemaUtil() {}

    // Called once per route at startup by ApiRegistry. The root of a schema file must be a JSON object
    public static JsonNode loadSchema(String classpathLocation) throws IOException {
        try (InputStream inputStream = new ClassPathResource(classpathLocation).getInputStream()) {
            JsonNode schema = objectMapper.readTree(inputStream);
            if (schema == null || !schema.isObject()) {
                throw new IOException("Schema file must be a JSON object: " + classpathLocation);
            }
            return schema;
        }
    }

    // True if the body is valid JSON matching the schema. An empty body or invalid JSON is always false,
    // so a route with a <schema> requires a JSON body
    public static boolean validate(JsonNode schema, String requestBody) {
        if (requestBody == null || requestBody.isBlank()) {
            return false;
        }
        try {
            return matches(schema, objectMapper.readTree(requestBody));
        } catch (IOException e) {
            return false;
        }
    }

    // Recursive check of one JSON value against one (sub)schema
    private static boolean matches(JsonNode schema, JsonNode data) {
        if (!matchesType(schema.get("type"), data)) {
            return false;
        }
        if (data.isObject()) {
            return matchesObject(schema, data);
        }
        // Without "items", array elements are not checked
        if (data.isArray() && schema.has("items")) {
            for (JsonNode item : data) {
                if (!matches(schema.get("items"), item)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean matchesObject(JsonNode schema, JsonNode data) {
        JsonNode properties = schema.path("properties");

        // A required field that is present with the value null passes here; its "type" then decides
        for (JsonNode field : schema.path("required")) {
            if (!data.has(field.asText())) {
                return false;
            }
        }

        boolean allowAdditional = schema.path("additionalProperties").asBoolean(false);
        Iterator<Map.Entry<String, JsonNode>> fields = data.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode fieldSchema = properties.get(field.getKey());

            if (fieldSchema == null) {
                if (!allowAdditional) {
                    return false;
                }
            } else if (!matches(fieldSchema, field.getValue())) {
                return false;
            }
        }
        return true;
    }

    // No "type" means any type; an array of types matches if any one of them matches
    private static boolean matchesType(JsonNode typeNode, JsonNode data) {
        if (typeNode == null) {
            return true;
        }
        if (typeNode.isArray()) {
            for (JsonNode type : typeNode) {
                if (matchesType(type.asText(), data)) {
                    return true;
                }
            }
            return false;
        }
        return matchesType(typeNode.asText(), data);
    }

    // "integer" accepts any number without a fractional part, so 2.0 counts as an integer (as in the standard)
    private static boolean matchesType(String type, JsonNode data) {
        return switch (type) {
            case "object" -> data.isObject();
            case "array" -> data.isArray();
            case "string" -> data.isTextual();
            case "integer" -> data.isNumber() && data.decimalValue().stripTrailingZeros().scale() <= 0;
            case "number" -> data.isNumber();
            case "boolean" -> data.isBoolean();
            case "null" -> data.isNull();
            default -> false;
        };
    }
}
