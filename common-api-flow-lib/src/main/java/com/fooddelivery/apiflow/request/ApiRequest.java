package com.fooddelivery.apiflow.request;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Collections;
import java.util.Map;

/**
 * Read-only view of one HTTP request: method, path, full URL, client IP, values looked up by name from query
 * parameters, headers, cookies, body fields and path variables, and the whole body as a DTO. It is the argument of
 * every business validator ({@code public boolean name(ApiRequest)}) and can also be a controller parameter.
 *
 * <p>One instance per request, shared through the request attribute {@link #ATTRIBUTE}, so the controller sees the
 * same values the validator checked. Parameters, headers, cookies and path variables come back as a
 * {@link RequestValue} (never {@code null}, converted with e.g. {@code .asInt()}); the other getters return
 * {@code null} when the value is absent. No getter throws. Not thread-safe.
 */
public class ApiRequest {
    // Request attribute key under which the instance for the current request is stored
    public static final String ATTRIBUTE = ApiRequest.class.getName();

    // USE_BIG_DECIMAL_FOR_FLOATS: decimals such as 0.1 are kept exactly, not rounded through double.
    // FAIL_ON_UNKNOWN_PROPERTIES off: getBody(Class) ignores body fields the target type does not declare
    private static final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final HttpServletRequest servletRequest;
    private final Map<String, String> pathVariables;
    private final String body;

    // Lazily parsed body; bodyJsonParsed tells "not parsed yet" apart from "parsed, result is null"
    private JsonNode bodyJson;
    private boolean bodyJsonParsed;

    /**
     * @param servletRequest the current request
     * @param pathVariables  variable name -> value, already extracted from the path (URL-decoded)
     * @param body           raw body cached by {@code RequestCachingFilter}; {@code null} is treated as {@code ""}
     */
    public ApiRequest(HttpServletRequest servletRequest, Map<String, String> pathVariables, String body) {
        this.servletRequest = servletRequest;
        this.pathVariables = Collections.unmodifiableMap(pathVariables);
        this.body = body == null ? "" : body;
    }

    // HTTP method, e.g. "POST"
    public String getMethod() { return servletRequest.getMethod(); }

    // Request URI without the query string, e.g. "/orders/7/items"
    public String getPath() { return servletRequest.getRequestURI(); }

    // URL as sent by the client, query string included, e.g. "http://localhost:8081/orders/7/items?page=1"
    public String getFullUrl() {
        String queryString = servletRequest.getQueryString();
        StringBuffer url = servletRequest.getRequestURL();
        return queryString == null ? url.toString() : url.append('?').append(queryString).toString();
    }

    // IP address of the direct client, IPv4 or IPv6, e.g. "127.0.0.1". Behind a proxy or gateway this is the proxy's
    // address; X-Forwarded-For is not read, because a client can forge it
    public String getRemoteAddr() { return servletRequest.getRemoteAddr(); }

    // Query parameter, e.g. ?page=1 -> getParam("page").asString() = "1", getParam("page").asInt() = 1.
    // First value if repeated. URL-decoded; form fields of an x-www-form-urlencoded body are included too
    public RequestValue getParam(String name) { return RequestValue.of(servletRequest.getParameter(name)); }

    // Header, name is case-insensitive, e.g. getHeader("x-client-id").asString(). First value if repeated
    public RequestValue getHeader(String name) { return RequestValue.of(servletRequest.getHeader(name)); }

    // Top-level field of the JSON body, e.g. {"price": 45000} -> getBody("price").decimalValue() = 45000.
    // null if the field is absent, or the body is empty or not a JSON object; a JSON null is returned as a NullNode
    public JsonNode getBody(String name) {
        JsonNode json = parseBody();
        return json == null || !json.isObject() ? null : json.get(name);
    }

    // Whole JSON body converted to a DTO, e.g. getBody(AddOrderItemRequest.class). Body fields the type does not
    // declare are ignored, declared fields missing from the body are null. null if the body is empty, not valid JSON,
    // or cannot be converted to the type (e.g. "abc" for a number field)
    public <T> T getBody(Class<T> type) {
        JsonNode json = parseBody();
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.treeToValue(json, type);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return null;
        }
    }

    // Cookie, name is case-sensitive, e.g. Cookie: refresh_token=abc -> getCookie("refresh_token").asString() = "abc".
    // First value if repeated (e.g. the same name set for two paths). Returned as sent, not URL-decoded
    public RequestValue getCookie(String name) {
        Cookie[] cookies = servletRequest.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(name)) {
                    return RequestValue.of(cookie.getValue());
                }
            }
        }
        return RequestValue.of(null);
    }

    // Variable of <path> in api-routes.xml, e.g. /orders/{id} + /orders/7 -> getPathVariable("id").asInt() = 7.
    // URL-decoded
    public RequestValue getPathVariable(String name) { return RequestValue.of(pathVariables.get(name)); }

    // Parsed once, on first call; null if the body is empty or not valid JSON
    private JsonNode parseBody() {
        if (!bodyJsonParsed) {
            bodyJsonParsed = true;
            if (!body.isBlank()) {
                try {
                    bodyJson = objectMapper.readTree(body);
                } catch (JsonProcessingException e) {
                    bodyJson = null;
                }
            }
        }
        return bodyJson;
    }
}
