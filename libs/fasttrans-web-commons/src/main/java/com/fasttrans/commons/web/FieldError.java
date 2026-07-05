package com.fasttrans.commons.web;

/**
 * One validation failure entry inside {@link ErrorBody#details()}.
 *
 * @param field   the request field that failed validation
 * @param message the validation message for that field
 */
public record FieldError(String field, String message) {
}
