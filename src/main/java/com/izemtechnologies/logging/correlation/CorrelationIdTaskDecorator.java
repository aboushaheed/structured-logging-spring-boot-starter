package com.izemtechnologies.logging.correlation;

import com.izemtechnologies.logging.context.RequestScopedLogContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * Task decorator that propagates correlation ID and request context to async tasks.
 * 
 * <p>When using @Async methods or ThreadPoolTaskExecutor, the correlation ID and
 * request-scoped tags would normally be lost because the task runs in a different thread.
 * This decorator captures the context before the task is submitted and restores it
 * when the task executes.</p>
 * 
 * <h2>Usage with @Async:</h2>
 * <pre>{@code
 * @Configuration
 * @EnableAsync
 * public class AsyncConfig implements AsyncConfigurer {
 *     
 *     @Autowired
 *     private CorrelationIdTaskDecorator taskDecorator;
 *     
 *     @Override
 *     public Executor getAsyncExecutor() {
 *         ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
 *         executor.setTaskDecorator(taskDecorator);
 *         executor.initialize();
 *         return executor;
 *     }
 * }
 * }</pre>
 * 
 * <h2>Usage with ThreadPoolTaskExecutor:</h2>
 * <pre>{@code
 * @Bean
 * public ThreadPoolTaskExecutor taskExecutor(CorrelationIdTaskDecorator decorator) {
 *     ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
 *     executor.setTaskDecorator(decorator);
 *     executor.setCorePoolSize(10);
 *     executor.setMaxPoolSize(20);
 *     executor.initialize();
 *     return executor;
 * }
 * }</pre>
 * 
 * @author Souidi
 * @since 1.1.0
 */
@Slf4j
public class CorrelationIdTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        // Capture context from the calling thread
        String correlationId = CorrelationIdHolder.get();
        Map<String, Object> requestContext = RequestScopedLogContext.snapshot();
        
        log.debug("Capturing context for async task: correlationId={}, contextSize={}", 
            correlationId, requestContext.size());
        
        return () -> {
            try {
                // Restore context in the executing thread
                if (correlationId != null) {
                    CorrelationIdHolder.set(correlationId);
                }
                if (!requestContext.isEmpty()) {
                    RequestScopedLogContext.restore(requestContext);
                }
                
                log.debug("Restored context in async thread: correlationId={}", correlationId);
                
                // Execute the actual task
                runnable.run();
                
            } finally {
                // Clean up to prevent memory leaks
                CorrelationIdHolder.clear();
                RequestScopedLogContext.clear();
            }
        };
    }
}
