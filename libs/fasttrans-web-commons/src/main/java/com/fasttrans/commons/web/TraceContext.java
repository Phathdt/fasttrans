package com.fasttrans.commons.web;

import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.UUID;

/**
 * Central helper for building {@link Meta} and rendering optional stack traces.
 *
 * <p>The traceId comes from the Micrometer {@link Tracer} when tracing is
 * configured (Phase 2). {@code Tracer} is resolved lazily through an
 * {@link ObjectProvider} so this component still works when no tracing bridge is
 * on the classpath (e.g. slice tests) — it falls back to a random UUID.
 */
@Component
public class TraceContext {

    private final ObjectProvider<Tracer> tracerProvider;
    private final boolean includeStackTrace;

    public TraceContext(
            ObjectProvider<Tracer> tracerProvider,
            @Value("${fasttrans.error.include-stacktrace:false}") boolean includeStackTrace) {
        this.tracerProvider = tracerProvider;
        this.includeStackTrace = includeStackTrace;
    }

    /** Build response metadata; requestId == current traceId (fallback UUID). */
    public Meta meta() {
        return new Meta(currentTraceId(), Instant.now().toString());
    }

    /**
     * Current traceId from the active span, or a random UUID when no span/tracer
     * is available. Kept public so handlers can reuse the same id for logging.
     */
    public String currentTraceId() {
        Tracer tracer = tracerProvider.getIfAvailable();
        if (tracer != null && tracer.currentSpan() != null) {
            return tracer.currentSpan().context().traceId();
        }
        return UUID.randomUUID().toString();
    }

    /** Rendered stack trace when the flag is on; null otherwise (omitted from JSON). */
    public String stackTraceOf(Throwable ex) {
        if (!includeStackTrace || ex == null) {
            return null;
        }
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
