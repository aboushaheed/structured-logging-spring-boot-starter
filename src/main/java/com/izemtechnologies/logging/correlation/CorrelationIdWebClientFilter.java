package com.izemtechnologies.logging.correlation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

/**
 * WebClient filter that propagates correlation IDs to outgoing HTTP requests.
 * 
 * <p>This filter automatically adds the current correlation ID to all outgoing
 * HTTP requests made via WebClient, enabling end-to-end distributed tracing
 * in reactive applications.</p>
 * 
 * <h2>Usage:</h2>
 * <pre>{@code
 * @Bean
 * public WebClient webClient(CorrelationIdWebClientFilter filter) {
 *     return WebClient.builder()
 *         .filter(filter)
 *         .build();
 * }
 * }</pre>
 * 
 * <p>Note: In reactive contexts, the correlation ID must be propagated through
 * the reactive context. This filter captures the correlation ID at subscription
 * time from the ThreadLocal.</p>
 * 
 * @author Souidi
 * @since 1.1.0
 */
@Slf4j
public class CorrelationIdWebClientFilter implements ExchangeFilterFunction {

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        return Mono.deferContextual(contextView -> {
            // Try to get correlation ID from reactive context first
            String correlationId = contextView.getOrDefault(
                CorrelationIdHolder.MDC_KEY, 
                CorrelationIdHolder.get() // Fallback to ThreadLocal
            );
            
            if (correlationId != null && !correlationId.isBlank()) {
                log.debug("Propagating correlation ID {} to {} {}", 
                    correlationId, request.method(), request.url());
                
                ClientRequest newRequest = ClientRequest.from(request)
                    .header(CorrelationIdHolder.HEADER_NAME, correlationId)
                    .header("X-Request-ID", correlationId)
                    .build();
                
                return next.exchange(newRequest);
            } else {
                log.debug("No correlation ID to propagate for {} {}", 
                    request.method(), request.url());
                return next.exchange(request);
            }
        });
    }

    /**
     * Creates an ExchangeFilterFunction that can be used directly with WebClient.
     * 
     * @return the exchange filter function
     */
    public static ExchangeFilterFunction create() {
        return new CorrelationIdWebClientFilter();
    }
}
