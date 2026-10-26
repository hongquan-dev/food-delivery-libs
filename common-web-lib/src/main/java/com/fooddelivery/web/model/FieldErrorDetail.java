package com.fooddelivery.web.model;

/**
 * One entry of {@code "errors"} in an {@link ApiResponse}: which field is wrong and why, so the frontend can show the
 * message under the right input.
 */
public record FieldErrorDetail(String field, String message) {
}
