package com.izemtechnologies.logging.sampling;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that ensures sampling context is properly cleaned up after each request.
 *
 * <p>This filter runs at the lowest precedence (last) to ensure that all sampling
 * contexts (head-based and tail-based) are properly cleaned up after request processing,
 * preventing memory leaks and cross-request contamination.</p>
 *
 * @author Souidi
 * @since 1.1.0
 */
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
public class SamplingContextCleanupFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            // CRITICAL: Always clean up sampling contexts to prevent memory leaks
            cleanupSamplingContexts();
        }
    }

    /**
     * Cleans up all thread-local sampling contexts.
     */
    private void cleanupSamplingContexts() {
        try {
            LogSampler.HeadSamplingContext.clear();
            LogSampler.TailSamplingContext.clear();
            log.trace("Sampling contexts cleaned up");
        } catch (Exception e) {
            log.debug("Error cleaning up sampling contexts: {}", e.getMessage());
        }
    }
}
