package com.fasttrans.commons.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verify ErrorCode enum constants map to correct HTTP statuses.
 */
class ErrorCodeTest {

    @Test
    void validationFailed_maps_to_400() {
        assertEquals(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED.status());
    }

    @Test
    void missingHeader_maps_to_400() {
        assertEquals(HttpStatus.BAD_REQUEST, ErrorCode.MISSING_HEADER.status());
    }

    @Test
    void unauthorized_maps_to_401() {
        assertEquals(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.status());
    }

    @Test
    void accountNotFound_maps_to_404() {
        assertEquals(HttpStatus.NOT_FOUND, ErrorCode.ACCOUNT_NOT_FOUND.status());
    }

    @Test
    void transferNotFound_maps_to_404() {
        assertEquals(HttpStatus.NOT_FOUND, ErrorCode.TRANSFER_NOT_FOUND.status());
    }

    @Test
    void ownershipDenied_maps_to_403() {
        assertEquals(HttpStatus.FORBIDDEN, ErrorCode.OWNERSHIP_DENIED.status());
    }

    @Test
    void accountUnavailable_maps_to_503() {
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.ACCOUNT_UNAVAILABLE.status());
    }

    @Test
    void internalError_maps_to_500() {
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR.status());
    }
}
