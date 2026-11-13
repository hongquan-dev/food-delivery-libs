package com.fooddelivery.dto.response;

/**
 * One entry of {@code "errors"} in an {@link ApiResponse}: which field is wrong and why, so the frontend can show the
 * message under the right input.
 *
 * @param field   name of the invalid field as sent by the client, e.g. {@code price} or {@code items[0].quantity}
 * @param message why the value is rejected, e.g. {@code must be greater than 0}
 */
public record FieldErrorDetail(String field, String message) {
}
