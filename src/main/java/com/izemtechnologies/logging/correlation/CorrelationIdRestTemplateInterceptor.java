package com.izemtechnologies.logging.correlation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * RestTemplate interceptor that propagates correlation IDs to outgoing HTTP requests.
 * 
 * <p>This interceptor automatically adds the current correlation ID to all outgoing
 * HTTP requests made via RestTemplate, enabling end-to-end distributed tracing.</p>
 * 
 * <h2>Usage:</h2>
 * <pre>{@code
 * @Bean
 * public RestTemplate restTemplate(CorrelationIdRestTemplateInterceptor interceptor) {
 *     RestTemplate restTemplate = new RestTemplate();
 *     restTemplate.getInterceptors().add(interceptor);
 *     return restTemplate;
 * }
 * }</pre>
 * 
 * <p>Or with RestTemplateBuilder:</p>
 * <pre>{@code
 * @Bean
 * public RestTemplate restTemplate(RestTemplateBuilder builder,
 *                                  CorrelationIdRestTemplateInterceptor interceptor) {
 *     return builder
 *         .additionalInterceptors(interceptor)
 *         .build();
 * }
 * }</pre>
 * 
 * @author Souidi
 * @since 1.1.0
 */
@Slf4j
public class CorrelationIdRestTemplateInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, 
                                        byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        
        // Get current correlation ID
        String correlationId = CorrelationIdHolder.get();
        
        if (correlationId != null && !correlationId.isBlank()) {
            // Add correlation ID to outgoing request
            request.getHeaders().add(CorrelationIdHolder.HEADER_NAME, correlationId);
            
            // Also add X-Request-ID for compatibility
            if (!request.getHeaders().containsKey("X-Request-ID")) {
                request.getHeaders().add("X-Request-ID", correlationId);
            }
            
            log.debug("Propagating correlation ID {} to {} {}", 
                correlationId, request.getMethod(), request.getURI());
        } else {
            log.debug("No correlation ID to propagate for {} {}", 
                request.getMethod(), request.getURI());
        }
        
        return execution.execute(request, body);
    }
}
