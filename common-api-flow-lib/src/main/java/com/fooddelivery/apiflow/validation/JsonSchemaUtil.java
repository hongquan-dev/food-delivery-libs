package com.fooddelivery.apiflow.validation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minimal JSON Schema checker for request bodies: structure and data types only; value rules belong in the route's
 * validator. Supports {@code type}, {@code required}, {@code properties}, {@code items} and
 * {@code additionalProperties} (boolean only), plus annotations such as {@code title} and {@code description}.
 *
 * <p>Any other keyword (e.g. {@code minLength}, {@code enum}, {@code pattern}) or an unknown {@code type} name is
 * rejected at startup by {@link #loadSchema}, so a schema never looks stricter than what is actually checked. Unlike
 * the standard, {@code additionalProperties} defaults to {@code false}: unknown fields are rejected at every level.
 */
public final class JsonSchemaUtil {
    // FAIL_ON_TRAILING_TOKENS: reject bodies such as {"a":1}garbage instead of reading only the first value
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private static final Set<String> CHECKED_KEYWORDS =
            Set.of("type", "required", "properties", "items", "additionalProperties");

    // Documentation only, never checked
    private static final Set<String> ANNOTATION_KEYWORDS =
            Set.of("$schema", "$id", "$comment", "title", "description", "examples", "default");

    private static final Set<String> TYPES =
            Set.of("object", "array", "string", "integer", "number", "boolean", "null");

    private JsonSchemaUtil() {}

    // Called once per route at startup by ApiRegistry. The root of a schema file must be a JSON object, and every
    // (sub)schema may only use the supported keywords
    public static JsonNode loadSchema(String classpathLocation) throws IOException {
        try (InputStream inputStream = new ClassPathResource(classpathLocation).getInputStream()) {
            JsonNode schema = objectMapper.readTree(inputStream);
            if (schema == null || !schema.isObject()) {
                throw new IOException("Schema file must be a JSON object: " + classpathLocation);
            }
            List<String> problems = new ArrayList<>();
            checkKeywords(schema, "#", problems);
            if (!problems.isEmpty()) {
                throw new IOException(String.join("; ", problems));
            }
            return schema;
        }
    }

    // Collects every unsupported keyword or malformed value of a (sub)schema, with its location,
    // e.g. "#/properties/username: unsupported keyword 'minLength'"
    private static void checkKeywords(JsonNode schema, String location, List<String> problems) {
        if (!schema.isObject()) {
            problems.add(location + ": a schema must be a JSON object");
            return;
        }
        schema.fieldNames().forEachRemaining(keyword -> {
            if (!CHECKED_KEYWORDS.contains(keyword) && !ANNOTATION_KEYWORDS.contains(keyword)) {
                problems.add(location + ": unsupported keyword '" + keyword + "'");
            }
        });

        JsonNode type = schema.get("type");
        if (type != null) {
            for (JsonNode name : type.isArray() ? type : List.of(type)) {
                if (!name.isTextual() || !TYPES.contains(name.asText())) {
                    problems.add(location + ": unknown type " + name + ", expected one of " + TYPES);
                }
            }
        }
        JsonNode additionalProperties = schema.get("additionalProperties");
        if (additionalProperties != null && !additionalProperties.isBoolean()) {
            problems.add(location + ": additionalProperties must be true or false");
        }
        JsonNode properties = schema.get("properties");
        if (properties != null) {
            properties.fields().forEachRemaining(property ->
                    checkKeywords(property.getValue(), location + "/properties/" + property.getKey(), problems));
        }
        JsonNode items = schema.get("items");
        if (items != null) {
            checkKeywords(items, location + "/items", problems);
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
