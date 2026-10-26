package com.fooddelivery.apiflow.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

/**
 * Read-only view of one HTTP request (path variables, query parameters, headers, body). It is the argument of every
 * business validator ({@code public boolean name(ValidationRequest)}) and can also be a controller parameter.
 *
 * <p>One instance per request, shared through the request attribute {@link #ATTRIBUTE}. Single-value getters return
 * {@code null} when absent and never throw. Not thread-safe.
 */
public class ValidationRequest {
    // Request attribute key under which the instance for the current request is stored
    public static final String ATTRIBUTE = ValidationRequest.class.getName();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpServletRequest servletRequest;
    private final Map<String, String> pathVariables;
    private final MultiValueMap<String, String> queryParams;
    private final HttpHeaders headers;
    private final String body;

    // Lazily parsed body; bodyJsonParsed tells "not parsed yet" apart from "parsed, result is null"
    private JsonNode bodyJson;
    private boolean bodyJsonParsed;

    /**
     * @param servletRequest the current request
     * @param pathVariables  variable name -> value, already extracted from the path (URL-decoded)
     * @param body           raw body cached by {@code RequestCachingFilter}; {@code null} is treated as {@code ""}
     */
    public ValidationRequest(HttpServletRequest servletRequest, Map<String, String> pathVariables, String body) {
        this.servletRequest = servletRequest;
        this.pathVariables = Collections.unmodifiableMap(pathVariables);
        this.queryParams = readQueryParams(servletRequest);
        this.headers = readHeaders(servletRequest);
        this.body = body == null ? "" : body;
    }

    // HTTP method, e.g. "POST"
    public String getMethod() { return servletRequest.getMethod(); }

    // Request URI without the query string, e.g. "/orders/7/items"
    public String getPath() { return servletRequest.getRequestURI(); }

    // Variables of <path> in api-routes.xml, e.g. /orders/{id} -> "id". Values are URL-decoded
    public Map<String, String> getPathVariables() { return pathVariables; }

    public String getPathVariable(String name) { return pathVariables.get(name); }

    // Request parameters, e.g. ?status=NEW&status=PAID -> "status" = [NEW, PAID]. Values are URL-decoded.
    // Read from the servlet parameter map, so form fields of an x-www-form-urlencoded body are included too
    public MultiValueMap<String, String> getQueryParams() { return queryParams; }

    // First value of the parameter, null if absent
    public String getQueryParam(String name) { return queryParams.getFirst(name); }

    // All headers; names are case-insensitive
    public HttpHeaders getHeaders() { return headers; }

    // First value of the header, null if absent
    public String getHeader(String name) { return headers.getFirst(name); }

    // Raw body, "" if the request has no JSON body (only JSON bodies are cached by RequestCachingFilter)
    public String getBody() { return body; }

    // Body parsed as JSON, null if the body is empty or not valid JSON. Parsed once, on first call
    public JsonNode getBodyJson() {
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

    // Underlying request, for anything not covered above (cookies, remote address, attributes...)
    public HttpServletRequest getServletRequest() { return servletRequest; }

    // Copied into an unmodifiable map so validators cannot change what the controller will see
    private static MultiValueMap<String, String> readQueryParams(HttpServletRequest request) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        request.getParameterMap().forEach((name, values) -> params.put(name, Arrays.asList(values)));
        return CollectionUtils.unmodifiableMultiValueMap(params);
    }

    private static HttpHeaders readHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            headers.addAll(name, Collections.list(request.getHeaders(name)));
        }
        return HttpHeaders.readOnlyHttpHeaders(headers);
    }
}
