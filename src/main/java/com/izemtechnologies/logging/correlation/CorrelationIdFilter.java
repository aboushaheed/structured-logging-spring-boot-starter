package com.izemtechnologies.logging.correlation;

import com.izemtechnologies.logging.properties.LoggingProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Servlet filter that manages correlation IDs for distributed tracing.
 * 
 * <p>This filter performs the following operations:</p>
 * <ol>
 *   <li>Extracts correlation ID from incoming request headers</li>
 *   <li>Generates a new correlation ID if none is present</li>
 *   <li>Stores the correlation ID in {@link CorrelationIdHolder} for thread-local access</li>
 *   <li>Adds the correlation ID to the response headers</li>
 *   <li>Cleans up the correlation ID after request processing</li>
 * </ol>
 * 
 * <p>The filter checks multiple header names for compatibility with different systems:</p>
 * <ul>
 *   <li>X-Correlation-ID (primary)</li>
 *   <li>X-Request-ID</li>
 *   <li>X-Trace-ID</li>
 *   <li>Request-ID</li>
 *   <li>Correlation-ID</li>
 * </ul>
 * 
 * @author Souidi
 * @since 1.1.0
 */
@Slf4j
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 1) // Run just after RequestLoggingFilter
public class CorrelationIdFilter extends OncePerRequestFilter {

    private final LoggingProperties properties;

    /**
     * Maximum allowed length for correlation IDs to prevent abuse.
     */
    private static final int MAX_CORRELATION_ID_LENGTH = 128;

    /**
     * Pattern for valid correlation IDs: alphanumeric, hyphens, underscores, and dots.
     * This prevents log injection attacks and ensures IDs are safe for logging.
     */
    private static final Pattern VALID_CORRELATION_ID_PATTERN =
        Pattern.compile("^[a-zA-Z0-9\\-_\\.]{1,128}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        
        try {
            // Extract or generate correlation ID
            String correlationId = extractCorrelationId(request);
            
            if (!StringUtils.hasText(correlationId)) {
                correlationId = CorrelationIdHolder.generate();
                log.debug("Generated new correlation ID: {}", correlationId);
            } else {
                log.debug("Using existing correlation ID from request: {}", correlationId);
            }
            
            // Store in thread-local and MDC
            CorrelationIdHolder.set(correlationId);
            
            // Add to response headers
            addCorrelationIdToResponse(response, correlationId);
            
            // Continue with the filter chain
            filterChain.doFilter(request, response);
            
        } finally {
            // CRITICAL: Always clear to prevent memory leaks and cross-request contamination
            CorrelationIdHolder.clear();
        }
    }

    /**
     * Extracts correlation ID from request headers.
     * Checks multiple header names for compatibility.
     * Validates the correlation ID format to prevent log injection attacks.
     *
     * @param request the HTTP request
     * @return the correlation ID, or null if not found or invalid
     */
    private String extractCorrelationId(HttpServletRequest request) {
        // Check primary header
        String correlationId = request.getHeader(CorrelationIdHolder.HEADER_NAME);

        if (StringUtils.hasText(correlationId)) {
            String validated = validateAndSanitize(correlationId);
            if (validated != null) {
                return validated;
            }
        }

        // Check alternative headers
        for (String headerName : CorrelationIdHolder.ALTERNATIVE_HEADERS) {
            correlationId = request.getHeader(headerName);
            if (StringUtils.hasText(correlationId)) {
                String validated = validateAndSanitize(correlationId);
                if (validated != null) {
                    return validated;
                }
            }
        }

        // Check custom header from configuration
        String customHeader = properties.getCorrelation().getHeaderName();
        if (StringUtils.hasText(customHeader)) {
            correlationId = request.getHeader(customHeader);
            if (StringUtils.hasText(correlationId)) {
                String validated = validateAndSanitize(correlationId);
                if (validated != null) {
                    return validated;
                }
            }
        }

        return null;
    }

    /**
     * Validates and sanitizes a correlation ID to prevent log injection attacks.
     *
     * @param correlationId the raw correlation ID from the request header
     * @return the sanitized correlation ID, or null if invalid
     */
    private String validateAndSanitize(String correlationId) {
        if (correlationId == null) {
            return null;
        }

        String trimmed = correlationId.trim();

        // Check length
        if (trimmed.isEmpty() || trimmed.length() > MAX_CORRELATION_ID_LENGTH) {
            log.warn("Correlation ID rejected: invalid length ({}). Generating new ID.",
                trimmed.length());
            return null;
        }

        // Validate format to prevent log injection
        if (!VALID_CORRELATION_ID_PATTERN.matcher(trimmed).matches()) {
            log.warn("Correlation ID rejected: invalid format. Generating new ID.");
            return null;
        }

        return trimmed;
    }

    /**
     * Adds correlation ID to response headers.
     * 
     * @param response      the HTTP response
     * @param correlationId the correlation ID to add
     */
    private void addCorrelationIdToResponse(HttpServletResponse response, String correlationId) {
        // Add primary header
        response.setHeader(CorrelationIdHolder.HEADER_NAME, correlationId);
        
        // Add custom header if configured
        String customHeader = properties.getCorrelation().getResponseHeaderName();
        if (StringUtils.hasText(customHeader) && !customHeader.equals(CorrelationIdHolder.HEADER_NAME)) {
            response.setHeader(customHeader, correlationId);
        }
        
        // Optionally add X-Request-ID as well
        if (properties.getCorrelation().isIncludeRequestIdHeader()) {
            response.setHeader("X-Request-ID", correlationId);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.getCorrelation().isEnabled()) {
            return true;
        }
        
        // Skip for excluded paths
        String path = request.getRequestURI();
        return properties.getCorrelation().getExcludePaths().stream()
            .anyMatch(pattern -> matchesPattern(path, pattern));
    }

    private boolean matchesPattern(String path, String pattern) {
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return path.startsWith(prefix);
        }
        if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            return path.startsWith(prefix) && !path.substring(prefix.length()).contains("/");
        }
        return path.equals(pattern);
    }
}
