package com.fooddelivery.web.model;

// One entry of "errors": which field is wrong and why, so the frontend can show it under the right input
public record FieldErrorDetail(String field, String message) {
}
