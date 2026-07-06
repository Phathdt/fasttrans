package com.fasttrans.commons.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verify GlobalExceptionHandler produces correct error envelopes for framework exceptions.
 * Uses a fake @RestController to trigger the exception handlers.
 */
@WebMvcTest(GlobalExceptionHandlerTest.FakeController.class)
class GlobalExceptionHandlerTest {

    @Configuration
    @Import({GlobalExceptionHandlerTest.FakeController.class, GlobalExceptionHandler.class, TraceContext.class, ErrorResponseFactory.class})
    static class TestConfig {
    }

    @Autowired MockMvc mockMvc;

    /**
     * Fake controller to trigger exception scenarios.
     */
    @RestController
    @RequestMapping("/fake")
    static class FakeController {

        @PostMapping("/required-header")
        public void requiresHeader(@RequestHeader("X-Required") String header) {
            // MissingRequestHeaderException when header is missing
        }

        @PostMapping("/validate-body")
        public void validateBody(@RequestBody @org.springframework.validation.annotation.Validated ValidRequest req) {
            // MethodArgumentNotValidException when validation fails
        }

        @GetMapping("/unhandled")
        public void throwUnhandled() throws Exception {
            throw new RuntimeException("Unexpected error");
        }

        @PostMapping("/parse-body")
        public void parseBody(@RequestBody ValidRequest req) {
            // HttpMessageNotReadableException when the JSON body is malformed
        }

        @GetMapping("/typed/{id}")
        public void typed(@PathVariable java.util.UUID id) {
            // MethodArgumentTypeMismatchException when {id} is not a UUID
        }
    }

    /**
     * DTO for validation testing.
     */
    public static class ValidRequest {
        @jakarta.validation.constraints.NotBlank
        public String name;
    }

    // ── MissingRequestHeaderException → 400 MISSING_HEADER ──

    @Test
    void missingHeader_returns_400_with_error_envelope() throws Exception {
        mockMvc.perform(post("/fake/required-header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty())
                .andExpect(jsonPath("$.error.code").value("MISSING_HEADER"))
                .andExpect(jsonPath("$.error.message").value(containsString("X-Required")))
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty())
                .andExpect(jsonPath("$.meta.timestamp").isNotEmpty());
    }

    // ── MethodArgumentNotValidException → 400 VALIDATION_FAILED ──

    @Test
    void validationFailed_returns_400_with_field_details() throws Exception {
        mockMvc.perform(post("/fake/validate-body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.message").value("Validation failed"))
                .andExpect(jsonPath("$.error.details", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.error.details[0].field").isNotEmpty())
                .andExpect(jsonPath("$.error.details[0].message").isNotEmpty())
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }

    // ── Uncaught Exception → 500 INTERNAL_ERROR ──

    @Test
    void uncaughtException_returns_500_with_generic_message() throws Exception {
        mockMvc.perform(get("/fake/unhandled"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("An unexpected error occurred. Please try again later."))
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty())
                .andExpect(jsonPath("$.meta.timestamp").isNotEmpty());
    }

    /**
     * stackTrace is null when flag is off (default).
     */
    @Test
    void stackTrace_omitted_from_json_when_flag_off() throws Exception {
        mockMvc.perform(get("/fake/unhandled"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.stackTrace").doesNotExist());
    }

    // ── Framework MVC exceptions keep their truthful 4xx status ──

    /** Malformed JSON body → 400, not 500. */
    @Test
    void malformedJson_returns_400_not_500() throws Exception {
        mockMvc.perform(post("/fake/parse-body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }

    /** Wrong HTTP verb → 405, not 500. */
    @Test
    void wrongMethod_returns_405_not_500() throws Exception {
        mockMvc.perform(get("/fake/parse-body"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }

    /** Non-UUID path variable → 400, not 500. */
    @Test
    void badPathVariableType_returns_400_not_500() throws Exception {
        mockMvc.perform(get("/fake/typed/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }
}
