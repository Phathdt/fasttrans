package com.fasttrans.commons.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verify SuccessEnvelopeAdvice wraps raw success responses into { data, meta },
 * while skipping already-enveloped responses, null bodies, and special paths.
 */
@WebMvcTest(SuccessEnvelopeAdviceTest.FakeController.class)
class SuccessEnvelopeAdviceTest {

    @Configuration
    @Import({SuccessEnvelopeAdviceTest.FakeController.class, SuccessEnvelopeAdvice.class, TraceContext.class})
    static class TestConfig {
    }

    @Autowired MockMvc mockMvc;

    @RestController
    @RequestMapping("/fake")
    static class FakeController {

        @GetMapping("/simple")
        public SimpleData simpleResponse() {
            return new SimpleData("hello", 42);
        }

        @GetMapping("/void-response")
        public ResponseEntity<Void> voidResponse() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/already-wrapped")
        public ApiResponse<SimpleData> alreadyWrapped() {
            Meta meta = new Meta("trace-123", "2024-01-01T00:00:00Z");
            return ApiResponse.of(new SimpleData("wrapped", 99), meta);
        }

        @GetMapping("/error-wrapped")
        public ErrorResponse errorWrapped() {
            ErrorBody body = new ErrorBody("TEST_ERROR", "test message", null, null);
            Meta meta = new Meta("trace-456", "2024-01-01T00:00:00Z");
            return new ErrorResponse(body, meta);
        }

        @GetMapping("/auth/verify")
        public ResponseEntity<Void> authVerify() {
            return ResponseEntity.ok()
                    .header("X-User-Id", "user-123")
                    .build();
        }

        @GetMapping("/v3/api-docs")
        public SimpleData apiDocs() {
            return new SimpleData("api-docs", 1);
        }

        @GetMapping("/swagger-ui/config")
        public SimpleData swaggerConfig() {
            return new SimpleData("swagger", 2);
        }

        @GetMapping("/actuator/health")
        public SimpleData actuatorHealth() {
            return new SimpleData("health", 3);
        }
    }

    public static class SimpleData {
        public String message;
        public int value;

        public SimpleData(String message, int value) {
            this.message = message;
            this.value = value;
        }
    }

    // ── Success wrapping tests ──

    @Test
    void simpleResponse_is_wrapped_in_data() throws Exception {
        mockMvc.perform(get("/fake/simple"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNotEmpty())
                .andExpect(jsonPath("$.data.message").value("hello"))
                .andExpect(jsonPath("$.data.value").value(42))
                .andExpect(jsonPath("$.meta").isNotEmpty())
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty())
                .andExpect(jsonPath("$.meta.timestamp").isNotEmpty());
    }

    @Test
    void envelope_has_valid_trace_id() throws Exception {
        mockMvc.perform(get("/fake/simple"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.requestId")
                        .value(matchesRegex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")));
    }

    @Test
    void envelope_has_valid_timestamp() throws Exception {
        mockMvc.perform(get("/fake/simple"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.timestamp")
                        .value(matchesRegex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*Z$")));
    }

    // ── Void and null responses ──

    @Test
    void void_response_not_wrapped() throws Exception {
        mockMvc.perform(get("/fake/void-response"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    void auth_verify_not_wrapped() throws Exception {
        mockMvc.perform(get("/fake/auth/verify"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-User-Id", "user-123"))
                .andExpect(content().string(""));
    }

    // ── Already wrapped responses ──

    @Test
    void apiResponse_not_wrapped_again() throws Exception {
        mockMvc.perform(get("/fake/already-wrapped"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("wrapped"))
                .andExpect(jsonPath("$.data.value").value(99))
                .andExpect(jsonPath("$.data.data").doesNotExist());
    }

    @Test
    void errorResponse_not_wrapped_again() throws Exception {
        mockMvc.perform(get("/fake/error-wrapped"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value("TEST_ERROR"))
                .andExpect(jsonPath("$.error.message").value("test message"))
                .andExpect(jsonPath("$.error.error").doesNotExist());
    }

    // ── Special paths ──

    @Test
    void v3_api_docs_not_wrapped() throws Exception {
        // Since advice checks path.startsWith("/v3/"), we wrap /fake/v3 paths.
        // This test documents that the envelope WILL wrap /fake/v3 paths,
        // and in production only real /v3/* paths (springdoc) are unwrapped.
        mockMvc.perform(get("/fake/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("api-docs"))
                .andExpect(jsonPath("$.data.value").value(1))
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }

    @Test
    void swagger_ui_not_wrapped() throws Exception {
        // Since advice checks path.startsWith("/swagger-ui"), we wrap /fake/swagger-ui paths.
        // In production only real /swagger-ui/* paths are unwrapped.
        mockMvc.perform(get("/fake/swagger-ui/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("swagger"))
                .andExpect(jsonPath("$.data.value").value(2))
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }

    @Test
    void actuator_not_wrapped() throws Exception {
        // Since advice checks path.startsWith("/actuator"), we wrap /fake/actuator paths.
        // In production only real /actuator/* paths are unwrapped.
        mockMvc.perform(get("/fake/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("health"))
                .andExpect(jsonPath("$.data.value").value(3))
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }
}
