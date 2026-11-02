package com.fooddelivery.apiflow.request;

/**
 * One text value of a request (query parameter, header or path variable), returned by {@link ApiRequest} so it can
 * be converted in the same expression, e.g. {@code request.getParam("page").asInt()}.
 *
 * <p>Never {@code null} itself, even when the value is absent, so calls can always be chained; the conversion methods
 * return {@code null} instead. Immutable.
 */
public final class RequestValue {
    private static final RequestValue ABSENT = new RequestValue(null);

    // Raw text as received (already URL-decoded), null if the request has no such value
    private final String value;

    private RequestValue(String value) {
        this.value = value;
    }

    // Package-private: only ApiRequest creates values. Absent values share one instance
    static RequestValue of(String value) {
        return value == null ? ABSENT : new RequestValue(value);
    }

    // Text as received, null if absent
    public String asString() { return value; }

    // Integer value, surrounding spaces ignored, e.g. " 12 " -> 12. null if absent or not an integer in int range
    // (e.g. "abc", "1.5", "99999999999")
    public Integer asInt() {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Same as asString(), so a value can be logged or concatenated directly; "null" if absent
    @Override
    public String toString() { return String.valueOf(value); }
}
