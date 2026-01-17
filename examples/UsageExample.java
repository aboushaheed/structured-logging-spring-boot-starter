package com.example.demo;

import com.izemtechnologies.logging.manager.LogMetadataManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Usage Examples for the Structured JSON Logging Starter
 * 
 * This file demonstrates how to use the logging-starter library in your
 * Spring Boot application. It covers:
 * 
 * 1. Injecting and using LogMetadataManager for dynamic runtime tags
 * 2. Using SLF4J 2.x fluent API with key-value pairs
 * 3. Request-scoped tags with automatic cleanup
 * 4. Multi-tenant logging scenarios
 */

// =============================================================================
// Main Application
// =============================================================================

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}

// =============================================================================
// Example 1: Basic Dynamic Tag Management
// =============================================================================

@Slf4j
@Service
@RequiredArgsConstructor
class TenantService {

    private final LogMetadataManager logMetadataManager;

    /**
     * Demonstrates setting a tenant_id tag at runtime.
     * This tag will appear in ALL subsequent log lines until removed.
     */
    public void setCurrentTenant(String tenantId) {
        // Add a dynamic tag - this will appear in every log line
        logMetadataManager.addTag("tenant_id", tenantId);
        
        log.info("Tenant context established");
        // Output: {"message":"Tenant context established","tenant_id":"tenant-123",...}
    }

    /**
     * Demonstrates removing a tag when tenant context is cleared.
     */
    public void clearTenantContext() {
        logMetadataManager.removeTag("tenant_id");
        log.info("Tenant context cleared");
    }
}

// =============================================================================
// Example 2: REST Controller with Request-Scoped Tags
// =============================================================================

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
class OrderController {

    private final LogMetadataManager logMetadataManager;
    private final OrderService orderService;

    /**
     * Demonstrates using temporary tags for request-scoped logging.
     * Tags are automatically removed after the request completes.
     */
    @PostMapping
    public OrderResponse createOrder(@RequestBody OrderRequest request,
                                     @RequestHeader("X-Tenant-Id") String tenantId,
                                     @RequestHeader("X-Correlation-Id") String correlationId) {
        
        // Use withTags for automatic cleanup after the block
        return logMetadataManager.withTags(
            Map.of(
                "tenant_id", tenantId,
                "correlation_id", correlationId,
                "order_type", request.type()
            ),
            () -> {
                log.info("Processing order request");
                // Output includes: tenant_id, correlation_id, order_type
                
                OrderResponse response = orderService.processOrder(request);
                
                log.info("Order created successfully");
                return response;
            }
        );
        // Tags are automatically removed here
    }

    /**
     * Demonstrates manual tag management with try-finally.
     */
    @GetMapping("/{orderId}")
    public OrderResponse getOrder(@PathVariable String orderId,
                                  @RequestHeader("X-Tenant-Id") String tenantId) {
        try {
            // Add tags manually
            logMetadataManager
                .addTag("tenant_id", tenantId)
                .addTag("order_id", orderId);
            
            log.info("Fetching order details");
            return orderService.getOrder(orderId);
            
        } finally {
            // Always clean up in finally block
            logMetadataManager.removeTags("tenant_id", "order_id");
        }
    }
}

// =============================================================================
// Example 3: SLF4J 2.x Fluent API Usage
// =============================================================================

@Slf4j
@Service
@RequiredArgsConstructor
class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    /**
     * Demonstrates SLF4J 2.x fluent API with addKeyValue().
     * Key-value pairs appear as top-level JSON fields.
     */
    public OrderResponse processOrder(OrderRequest request) {
        String orderId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();
        
        // SLF4J 2.x fluent API - key-value pairs become JSON fields
        logger.atInfo()
            .addKeyValue("order_id", orderId)
            .addKeyValue("customer_id", request.customerId())
            .addKeyValue("item_count", request.items().size())
            .addKeyValue("total_amount", request.totalAmount())
            .log("Order processing started");
        
        // Output:
        // {
        //   "message": "Order processing started",
        //   "order_id": "abc-123",
        //   "customer_id": "cust-456",
        //   "item_count": 3,
        //   "total_amount": 99.99,
        //   ...
        // }
        
        // Simulate processing
        processItems(request);
        
        long duration = System.currentTimeMillis() - startTime;
        
        // Log completion with metrics
        logger.atInfo()
            .addKeyValue("order_id", orderId)
            .addKeyValue("duration_ms", duration)
            .addKeyValue("status", "completed")
            .log("Order processing completed");
        
        return new OrderResponse(orderId, "COMPLETED");
    }

    public OrderResponse getOrder(String orderId) {
        logger.atDebug()
            .addKeyValue("order_id", orderId)
            .log("Retrieving order from database");
        
        // Simulate retrieval
        return new OrderResponse(orderId, "ACTIVE");
    }

    private void processItems(OrderRequest request) {
        for (int i = 0; i < request.items().size(); i++) {
            OrderItem item = request.items().get(i);
            
            logger.atDebug()
                .addKeyValue("item_index", i)
                .addKeyValue("product_id", item.productId())
                .addKeyValue("quantity", item.quantity())
                .log("Processing order item");
        }
    }
}

// =============================================================================
// Example 4: Background Job with Dynamic Tags
// =============================================================================

@Slf4j
@Service
@RequiredArgsConstructor
class BatchProcessingService implements CommandLineRunner {

    private final LogMetadataManager logMetadataManager;

    @Override
    public void run(String... args) {
        // Set job-level tags that persist for the entire batch
        String jobId = UUID.randomUUID().toString();
        logMetadataManager
            .addTag("job_id", jobId)
            .addTag("job_type", "order_sync");
        
        log.info("Batch job started");
        
        try {
            processBatch();
        } finally {
            log.info("Batch job completed");
            logMetadataManager.removeTags("job_id", "job_type");
        }
    }

    private void processBatch() {
        for (int i = 0; i < 10; i++) {
            // Add batch-item specific tag
            logMetadataManager.addTag("batch_item", i);
            
            log.info("Processing batch item");
            // Output includes: job_id, job_type, batch_item
            
            // Simulate work
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        logMetadataManager.removeTag("batch_item");
    }
}

// =============================================================================
// Example 5: Multi-Cluster Deployment Tags
// =============================================================================

@Slf4j
@Service
@RequiredArgsConstructor
class ClusterAwareService {

    private final LogMetadataManager logMetadataManager;

    /**
     * Demonstrates setting cluster-specific tags at application startup.
     * These tags identify which cluster/pod is generating the logs.
     */
    public void initializeClusterContext(String clusterId, String podName, String nodeId) {
        logMetadataManager
            .addTag("cluster_id", clusterId)
            .addTag("pod_name", podName)
            .addTag("node_id", nodeId);
        
        log.info("Cluster context initialized");
        // All subsequent logs will include cluster_id, pod_name, node_id
    }

    /**
     * Demonstrates adding request-specific tags on top of cluster tags.
     */
    public void handleRequest(String requestId, String userId) {
        // These are added on top of the existing cluster tags
        logMetadataManager.withTags(
            Map.of(
                "request_id", requestId,
                "user_id", userId
            ),
            () -> {
                log.info("Handling user request");
                // Output includes: cluster_id, pod_name, node_id, request_id, user_id
                
                performBusinessLogic();
            }
        );
    }

    private void performBusinessLogic() {
        log.debug("Executing business logic");
    }
}

// =============================================================================
// Example 6: Error Logging with Context
// =============================================================================

@Slf4j
@Service
@RequiredArgsConstructor
class ErrorHandlingService {

    private static final Logger logger = LoggerFactory.getLogger(ErrorHandlingService.class);

    /**
     * Demonstrates error logging with rich context using fluent API.
     */
    public void processWithErrorHandling(String entityId) {
        try {
            riskyOperation(entityId);
        } catch (Exception e) {
            // Log error with full context
            logger.atError()
                .addKeyValue("entity_id", entityId)
                .addKeyValue("error_type", e.getClass().getSimpleName())
                .addKeyValue("error_code", "PROC_001")
                .addKeyValue("recoverable", true)
                .setCause(e)
                .log("Failed to process entity");
            
            // Output:
            // {
            //   "level": "ERROR",
            //   "message": "Failed to process entity",
            //   "entity_id": "ent-123",
            //   "error_type": "IllegalStateException",
            //   "error_code": "PROC_001",
            //   "recoverable": true,
            //   "stack_trace": "...",
            //   ...
            // }
        }
    }

    private void riskyOperation(String entityId) {
        throw new IllegalStateException("Simulated error for " + entityId);
    }
}

// =============================================================================
// Supporting Record Classes
// =============================================================================

record OrderRequest(
    String customerId,
    String type,
    java.util.List<OrderItem> items,
    double totalAmount
) {}

record OrderItem(
    String productId,
    int quantity,
    double price
) {}

record OrderResponse(
    String orderId,
    String status
) {}
