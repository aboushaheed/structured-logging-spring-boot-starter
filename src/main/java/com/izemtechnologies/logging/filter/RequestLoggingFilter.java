package com.izemtechnologies.logging.filter;

import com.izemtechnologies.logging.context.RequestScopedLogContext;
import com.izemtechnologies.logging.correlation.CorrelationIdHolder;
import com.izemtechnologies.logging.properties.LoggingProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StopWatch;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that initializes and cleans up the request-scoped logging context.
 * 
 * <p>This filter runs at the highest precedence to ensure the logging context
 * is available for all subsequent filters and handlers.</p>
 * 
 * <p>It automatically populates common request metadata:</p>
 * <ul>
 *   <li>{@code request_id} - Unique identifier for this request</li>
 *   <li>{@code method} - HTTP method (GET, POST, etc.)</li>
 *   <li>{@code uri} - Request URI path</li>
 *   <li>{@code query} - Query string (if present)</li>
 *   <li>{@code client_ip} - Client IP address (with proxy support)</li>
 *   <li>{@code user_agent} - User-Agent header</li>
 * </ul>
 * 
 * @author Souidi
 * @since 1.1.0
 */
@Slf4j
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String REAL_IP_HEADER = "X-Real-IP";

    private final LoggingProperties properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        
        try {
            // Initialize the request-scoped context
            RequestScopedLogContext.initialize();
            
            // Generate or extract request ID
            String requestId = extractOrGenerateRequestId(request);
            
            // Populate standard request metadata
            populateRequestMetadata(request, requestId);
            
            // Add request ID to response header for tracing
            response.setHeader(REQUEST_ID_HEADER, requestId);
            
            // Continue with the filter chain
            filterChain.doFilter(request, response);
            
        } finally {
            stopWatch.stop();
            
            // Log request completion if enabled
            if (properties.getRequestContext().isLogRequestCompletion()) {
                logRequestCompletion(request, response, stopWatch.getTotalTimeMillis());
            }
            
            // CRITICAL: Always clear the context to prevent memory leaks
            RequestScopedLogContext.clear();
            CorrelationIdHolder.clear();
        }
    }

    private void populateRequestMetadata(HttpServletRequest request, String requestId) {
        RequestScopedLogContext.put("request_id", requestId);
        RequestScopedLogContext.put("method", request.getMethod());
        RequestScopedLogContext.put("uri", request.getRequestURI());
        
        // Query string (if present)
        if (StringUtils.hasText(request.getQueryString())) {
            RequestScopedLogContext.put("query", request.getQueryString());
        }
        
        // Client IP (with proxy support)
        String clientIp = getClientIp(request);
        RequestScopedLogContext.put("client_ip", clientIp);
        
        // User-Agent
        String userAgent = request.getHeader("User-Agent");
        if (StringUtils.hasText(userAgent)) {
            RequestScopedLogContext.put("user_agent", truncate(userAgent, 200));
        }
        
        // Content-Type
        if (StringUtils.hasText(request.getContentType())) {
            RequestScopedLogContext.put("content_type", request.getContentType());
        }
        
        // Custom headers to capture (configurable)
        for (String headerName : properties.getRequestContext().getCaptureHeaders()) {
            String headerValue = request.getHeader(headerName);
            if (StringUtils.hasText(headerValue)) {
                String sanitizedName = headerName.toLowerCase().replace("-", "_");
                RequestScopedLogContext.put("header_" + sanitizedName, headerValue);
            }
        }
    }

    private String extractOrGenerateRequestId(HttpServletRequest request) {
        // Check for existing request ID header
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        
        if (!StringUtils.hasText(requestId)) {
            // Generate a new request ID
            requestId = generateRequestId();
        }
        
        return requestId;
    }

    private String generateRequestId() {
        // Format: timestamp prefix for sortability + random suffix for uniqueness
        long timestamp = System.currentTimeMillis();
        String random = UUID.randomUUID().toString().substring(0, 8);
        return String.format("%d-%s", timestamp, random);
    }

    private String getClientIp(HttpServletRequest request) {
        // Check X-Forwarded-For header (may contain multiple IPs)
        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (StringUtils.hasText(forwardedFor)) {
            // Take the first IP (original client)
            String[] ips = forwardedFor.split(",");
            return ips[0].trim();
        }
        
        // Check X-Real-IP header
        String realIp = request.getHeader(REAL_IP_HEADER);
        if (StringUtils.hasText(realIp)) {
            return realIp;
        }
        
        // Fall back to remote address
        return request.getRemoteAddr();
    }

    private void logRequestCompletion(HttpServletRequest request, 
                                      HttpServletResponse response,
                                      long durationMs) {
        int status = response.getStatus();
        String level = status >= 500 ? "ERROR" : (status >= 400 ? "WARN" : "INFO");
        
        RequestScopedLogContext.put("status", String.valueOf(status));
        RequestScopedLogContext.put("duration_ms", String.valueOf(durationMs));
        
        String message = String.format("Request completed: %s %s -> %d (%dms)",
            request.getMethod(), request.getRequestURI(), status, durationMs);
        
        switch (level) {
            case "ERROR" -> log.error(message);
            case "WARN" -> log.warn(message);
            default -> log.info(message);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Skip filtering for excluded paths
        String path = request.getRequestURI();
        return properties.getRequestContext().getExcludePaths().stream()
            .anyMatch(pattern -> matchesPattern(path, pattern));
    }

    private boolean matchesPattern(String path, String pattern) {
        // Simple ant-style pattern matching
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
