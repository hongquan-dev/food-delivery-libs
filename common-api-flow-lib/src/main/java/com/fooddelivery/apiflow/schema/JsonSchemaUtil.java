package com.fooddelivery.apiflow.schema;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;

public final class JsonSchemaUtil {
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private JsonSchemaUtil() {}

    // Load a schema file from the classpath
    public static JsonNode loadSchema(String classpathLocation) throws IOException {
        try (InputStream inputStream = new ClassPathResource(classpathLocation).getInputStream()) {
            JsonNode schema = objectMapper.readTree(inputStream);
            if (schema == null || !schema.isObject()) {
                throw new IOException("Schema file must be a JSON object: " + classpathLocation);
            }
            return schema;
        }
    }

    // True if the body matches the schema, false otherwise
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

    private static boolean matches(JsonNode schema, JsonNode data) {
        if (!matchesType(schema.get("type"), data)) {
            return false;
        }
        if (data.isObject()) {
            return matchesObject(schema, data);
        }
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
