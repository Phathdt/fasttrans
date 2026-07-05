package com.fasttrans.commons.web;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Wraps every JSON success body into {@link ApiResponse} ({@code { data, meta }}).
 *
 * <p>Skips bodies that are already enveloped ({@link ApiResponse}/{@link ErrorResponse}
 * — the latter produced by the exception handlers) and non-JSON / body-less
 * responses such as the header-only {@code /auth/verify} ForwardAuth endpoint
 * (returns {@code ResponseEntity<Void>} → null body, never wrapped).
 */
@RestControllerAdvice
public class SuccessEnvelopeAdvice implements ResponseBodyAdvice<Object> {

    private final TraceContext traceContext;

    public SuccessEnvelopeAdvice(TraceContext traceContext) {
        this.traceContext = traceContext;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // Only wrap JSON responses; leave String/byte/other converters untouched.
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        // Already enveloped (success or error handler output) → leave as-is.
        if (body instanceof ApiResponse<?> || body instanceof ErrorResponse) {
            return body;
        }
        // Header-only / body-less responses (e.g. /auth/verify) → nothing to wrap.
        if (body == null) {
            return null;
        }
        // Only wrap JSON payloads; skip String and binary converters so we don't
        // break text/plain or the actuator.
        if (!MediaType.APPLICATION_JSON.isCompatibleWith(selectedContentType)) {
            return body;
        }
        // Skip springdoc and actuator internal endpoints — they manage their own
        // response shape and must not be intercepted by the envelope advice.
        String path = request.getURI().getPath();
        if (path.startsWith("/v3/") || path.startsWith("/swagger-ui") || path.startsWith("/actuator")) {
            return body;
        }
        return ApiResponse.of(body, traceContext.meta());
    }
}
