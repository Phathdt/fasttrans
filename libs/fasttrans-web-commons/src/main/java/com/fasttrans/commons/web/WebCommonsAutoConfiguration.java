package com.fasttrans.commons.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Registers the web-commons beans into any service that depends on this library,
 * without requiring the service to change its {@code @ComponentScan}. Declared in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 */
@AutoConfiguration
@Import({
        TraceContext.class,
        ErrorResponseFactory.class,
        SuccessEnvelopeAdvice.class,
        GlobalExceptionHandler.class
})
public class WebCommonsAutoConfiguration {
}
