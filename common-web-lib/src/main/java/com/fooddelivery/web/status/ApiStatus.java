package com.fooddelivery.web.status;

// HTTP statuses used by the APIs of the project, passed to ResponseHandler.
// code: default value of the "code" field in the response body for this status
public enum ApiStatus {

    // 2xx: success
    OK(200, "OK", "SUCCESS"),
    CREATED(201, "Created", "SUCCESS"),
    ACCEPTED(202, "Accepted", "SUCCESS"),

    // 4xx: client error
    BAD_REQUEST(400, "Bad Request", "BAD_REQUEST"),
    UNAUTHORIZED(401, "Unauthorized", "UNAUTHORIZED"),
    FORBIDDEN(403, "Forbidden", "FORBIDDEN"),
    NOT_FOUND(404, "Not Found", "NOT_FOUND"),
    METHOD_NOT_ALLOWED(405, "Method Not Allowed", "METHOD_NOT_ALLOWED"),
    CONFLICT(409, "Conflict", "CONFLICT"),
    PAYLOAD_TOO_LARGE(413, "Payload Too Large", "PAYLOAD_TOO_LARGE"),
    UNSUPPORTED_MEDIA_TYPE(415, "Unsupported Media Type", "UNSUPPORTED_MEDIA_TYPE"),
    UNPROCESSABLE_ENTITY(422, "Unprocessable Entity", "UNPROCESSABLE_ENTITY"),
    TOO_MANY_REQUESTS(429, "Too Many Requests", "TOO_MANY_REQUESTS"),

    // 5xx: server error
    INTERNAL_SERVER_ERROR(500, "Internal Server Error", "INTERNAL_ERROR"),
    BAD_GATEWAY(502, "Bad Gateway", "BAD_GATEWAY"),
    SERVICE_UNAVAILABLE(503, "Service Unavailable", "SERVICE_UNAVAILABLE"),
    GATEWAY_TIMEOUT(504, "Gateway Timeout", "GATEWAY_TIMEOUT");

    private final int value;
    private final String reasonPhrase;
    private final String code;

    ApiStatus(int value, String reasonPhrase, String code) {
        this.value = value;
        this.reasonPhrase = reasonPhrase;
        this.code = code;
    }

    public int value() { return value; }

    public String reasonPhrase() { return reasonPhrase; }

    public String code() { return code; }

    public boolean isSuccess() { return value >= 200 && value < 300; }
}
