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

// Everything about one request: path variables, query parameters, headers and body.
// Passed to validators, and can also be declared as a controller method parameter
public class ValidationRequest {
    // Request attribute holding the instance built for the validator, reused by the controller
    public static final String ATTRIBUTE = ValidationRequest.class.getName();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpServletRequest servletRequest;
    private final Map<String, String> pathVariables;
    private final MultiValueMap<String, String> queryParams;
    private final HttpHeaders headers;
    private final String body;

    private JsonNode bodyJson;
    private boolean bodyJsonParsed;

    public ValidationRequest(HttpServletRequest servletRequest, Map<String, String> pathVariables, String body) {
        this.servletRequest = servletRequest;
        this.pathVariables = Collections.unmodifiableMap(pathVariables);
        this.queryParams = readQueryParams(servletRequest);
        this.headers = readHeaders(servletRequest);
        this.body = body == null ? "" : body;
    }

    public String getMethod() { return servletRequest.getMethod(); }

    public String getPath() { return servletRequest.getRequestURI(); }

    // Variables in <path> of api-routes.xml, e.g. /orders/{id} -> "id". Values are URL-decoded
    public Map<String, String> getPathVariables() { return pathVariables; }

    public String getPathVariable(String name) { return pathVariables.get(name); }

    // Query string parameters, e.g. ?status=NEW&status=PAID -> "status" = [NEW, PAID]. Values are URL-decoded
    public MultiValueMap<String, String> getQueryParams() { return queryParams; }

    // First value of the parameter, null if absent
    public String getQueryParam(String name) { return queryParams.getFirst(name); }

    // Header names are case-insensitive
    public HttpHeaders getHeaders() { return headers; }

    // First value of the header, null if absent
    public String getHeader(String name) { return headers.getFirst(name); }

    // Raw body, "" if the request has no JSON body
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
