package com.izemtechnologies.logging.config;

import com.izemtechnologies.logging.properties.LoggingProperties;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Auto-configuration for distributed tracing integration.
 * <p>
 * This configuration automatically adds TraceID and SpanID to the MDC
 * (Mapped Diagnostic Context) so they appear in all JSON log lines.
 * </p>
 *
 * <h3>Supported Tracing Libraries:</h3>
 * <ul>
 *     <li>Micrometer Tracing (with Brave or OpenTelemetry bridge)</li>
 * </ul>
 *
 * @author Logging Starter Team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(name = "io.micrometer.tracing.Tracer")
@ConditionalOnProperty(prefix = "app.logging.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TracingAutoConfiguration {

    /**
     * Creates a filter that populates MDC with tracing information.
     *
     * @param tracer the Micrometer Tracer (if available)
     * @param properties the logging properties
     * @return the MDC population filter
     */
    @Bean
    @ConditionalOnBean(Tracer.class)
    public TracingMdcFilter tracingMdcFilter(Tracer tracer, LoggingProperties properties) {
        LoggingProperties.TracingProperties tracingProps = properties.getTracing();
        log.debug("Creating TracingMdcFilter with traceIdField={}, spanIdField={}",
                tracingProps.getTraceIdField(), tracingProps.getSpanIdField());
        return new TracingMdcFilter(tracer, tracingProps);
    }

    /**
     * Servlet filter that populates MDC with trace and span IDs.
     */
    @Slf4j
    @RequiredArgsConstructor
    public static class TracingMdcFilter extends OncePerRequestFilter {

        private final Tracer tracer;
        private final LoggingProperties.TracingProperties tracingProps;

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            try {
                populateMdc();
                filterChain.doFilter(request, response);
            } finally {
                clearMdc();
            }
        }

        /**
         * Populates MDC with current trace context information.
         */
        private void populateMdc() {
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null) {
                TraceContext context = currentSpan.context();
                if (context != null) {
                    String traceId = context.traceId();
                    String spanId = context.spanId();
                    
                    if (traceId != null) {
                        MDC.put(tracingProps.getTraceIdField(), traceId);
                    }
                    if (spanId != null) {
                        MDC.put(tracingProps.getSpanIdField(), spanId);
                    }
                    
                    log.trace("Populated MDC with traceId={}, spanId={}", traceId, spanId);
                }
            }
        }

        /**
         * Clears tracing information from MDC.
         */
        private void clearMdc() {
            MDC.remove(tracingProps.getTraceIdField());
            MDC.remove(tracingProps.getSpanIdField());
        }
    }

    /**
     * Helper class for manual MDC population in non-web contexts.
     */
    @RequiredArgsConstructor
    public static class TracingMdcHelper {

        private final Tracer tracer;
        private final LoggingProperties.TracingProperties tracingProps;

        /**
         * Populates MDC with current trace context.
         * Call this at the start of async operations or background tasks.
         */
        public void populateMdc() {
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null) {
                TraceContext context = currentSpan.context();
                if (context != null) {
                    if (context.traceId() != null) {
                        MDC.put(tracingProps.getTraceIdField(), context.traceId());
                    }
                    if (context.spanId() != null) {
                        MDC.put(tracingProps.getSpanIdField(), context.spanId());
                    }
                }
            }
        }

        /**
         * Clears tracing information from MDC.
         * Call this at the end of async operations or background tasks.
         */
        public void clearMdc() {
            MDC.remove(tracingProps.getTraceIdField());
            MDC.remove(tracingProps.getSpanIdField());
        }

        /**
         * Executes a runnable with MDC populated.
         *
         * @param runnable the runnable to execute
         */
        public void withMdc(Runnable runnable) {
            try {
                populateMdc();
                runnable.run();
            } finally {
                clearMdc();
            }
        }
    }

    /**
     * Creates a helper bean for manual MDC population.
     *
     * @param tracer the Micrometer Tracer
     * @param properties the logging properties
     * @return the TracingMdcHelper instance
     */
    @Bean
    @ConditionalOnBean(Tracer.class)
    public TracingMdcHelper tracingMdcHelper(Tracer tracer, LoggingProperties properties) {
        return new TracingMdcHelper(tracer, properties.getTracing());
    }
}
