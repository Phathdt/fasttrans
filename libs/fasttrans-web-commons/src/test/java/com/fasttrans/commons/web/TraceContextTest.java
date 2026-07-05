package com.fasttrans.commons.web;

import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verify TraceContext builds correct meta with traceId fallback and stackTrace flag control.
 */
class TraceContextTest {

    /**
     * stackTrace flag OFF (default): stackTraceOf() returns null.
     */
    @Test
    void stackTraceFlag_off_returns_null() {
        ObjectProvider<Tracer> tracerProvider = mock(ObjectProvider.class);
        TraceContext context = new TraceContext(tracerProvider, false);

        String stackTrace = context.stackTraceOf(new RuntimeException("test"));
        assertNull(stackTrace, "stackTrace should be null when flag is off");
    }

    /**
     * stackTrace flag ON: stackTraceOf() renders the exception as a string.
     */
    @Test
    void stackTraceFlag_on_returns_stacktrace_string() {
        ObjectProvider<Tracer> tracerProvider = mock(ObjectProvider.class);
        TraceContext context = new TraceContext(tracerProvider, true);

        RuntimeException ex = new RuntimeException("test error");
        String stackTrace = context.stackTraceOf(ex);

        assertNotNull(stackTrace, "stackTrace should not be null when flag is on");
        assertTrue(stackTrace.contains("test error"), "stackTrace should contain exception message");
        assertTrue(stackTrace.contains("RuntimeException"), "stackTrace should contain exception class");
    }

    /**
     * stackTraceOf() with null exception returns null regardless of flag.
     */
    @Test
    void stackTraceOf_null_exception_returns_null() {
        ObjectProvider<Tracer> tracerProvider = mock(ObjectProvider.class);
        TraceContext context = new TraceContext(tracerProvider, true);

        String stackTrace = context.stackTraceOf(null);
        assertNull(stackTrace, "stackTrace should be null when exception is null");
    }

    /**
     * currentTraceId() returns UUID when Tracer is not available.
     */
    @Test
    void currentTraceId_fallback_uuid_when_no_tracer() {
        ObjectProvider<Tracer> tracerProvider = mock(ObjectProvider.class);
        when(tracerProvider.getIfAvailable()).thenReturn(null);

        TraceContext context = new TraceContext(tracerProvider, false);
        String traceId = context.currentTraceId();

        assertNotNull(traceId, "traceId should not be null");
        // UUID format: 8-4-4-4-12 hex digits
        assertTrue(traceId.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"),
                "traceId should be a valid UUID format");
    }

    /**
     * meta() returns Meta with non-null traceId and timestamp.
     */
    @Test
    void meta_returns_valid_meta() {
        ObjectProvider<Tracer> tracerProvider = mock(ObjectProvider.class);
        when(tracerProvider.getIfAvailable()).thenReturn(null);

        TraceContext context = new TraceContext(tracerProvider, false);
        Meta meta = context.meta();

        assertNotNull(meta, "meta should not be null");
        assertNotNull(meta.requestId(), "requestId should not be null");
        assertNotNull(meta.timestamp(), "timestamp should not be null");

        // requestId == traceId (UUID when no Tracer)
        assertTrue(meta.requestId().matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"),
                "requestId should be a valid UUID");

        // timestamp == ISO-8601 instant
        Instant parsed = Instant.parse(meta.timestamp());
        assertNotNull(parsed, "timestamp should be parseable as ISO-8601");
    }
}
