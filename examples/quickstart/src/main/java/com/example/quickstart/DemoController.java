package com.example.quickstart;

import com.izemtechnologies.logging.correlation.CorrelationIdHolder;
import com.izemtechnologies.logging.manager.LogMetadataManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Demo controller showing structured logging features.
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DemoController {

    private final LogMetadataManager logMetadataManager;

    // =========================================================================
    // Example 1: Basic Logging (automatic JSON output)
    // =========================================================================

    /**
     * Basic endpoint - logs are automatically in JSON format.
     *
     * Test: curl http://localhost:8080/api/hello
     *
     * Log output:
     * {
     *   "@timestamp": "2024-01-15T10:30:45.123Z",
     *   "level": "INFO",
     *   "message": "Hello endpoint called",
     *   "application": "logging-quickstart",
     *   "env": "development",
     *   "correlationId": "1705312245000-a1b2c3d4"
     * }
     */
    @GetMapping("/hello")
    public ResponseEntity<Map<String, String>> hello() {
        log.info("Hello endpoint called");

        String correlationId = CorrelationIdHolder.get();
        return ResponseEntity.ok(Map.of(
            "message", "Hello World!",
            "correlationId", correlationId
        ));
    }

    // =========================================================================
    // Example 2: SLF4J 2.x Fluent API with Key-Value Pairs
    // =========================================================================

    /**
     * Using SLF4J 2.x fluent API - key-values appear at JSON root.
     *
     * Test: curl http://localhost:8080/api/orders/ORD-123
     *
     * Log output:
     * {
     *   "message": "Fetching order",
     *   "order_id": "ORD-123",
     *   "operation": "get_order",
     *   ...
     * }
     */
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<Map<String, String>> getOrder(@PathVariable String orderId) {
        log.atInfo()
            .addKeyValue("order_id", orderId)
            .addKeyValue("operation", "get_order")
            .log("Fetching order");

        // Simulate business logic
        log.atDebug()
            .addKeyValue("order_id", orderId)
            .addKeyValue("step", "database_query")
            .log("Querying database for order");

        return ResponseEntity.ok(Map.of(
            "orderId", orderId,
            "status", "COMPLETED"
        ));
    }

    // =========================================================================
    // Example 3: Dynamic Tags with LogMetadataManager
    // =========================================================================

    /**
     * Using LogMetadataManager for request-scoped tags.
     *
     * Test: curl -X POST http://localhost:8080/api/orders \
     *            -H "Content-Type: application/json" \
     *            -H "X-Tenant-ID: tenant-123" \
     *            -d '{"customerId":"CUST-456","amount":99.99}'
     *
     * Log output includes tenant_id and customer_id in all logs within the request.
     */
    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> createOrder(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "default") String tenantId) {

        // Tags are automatically cleaned up after the lambda
        return logMetadataManager.withTags(
            Map.of(
                "tenant_id", tenantId,
                "customer_id", request.get("customerId")
            ),
            () -> {
                log.info("Creating new order");

                // All logs in this block include tenant_id and customer_id
                processOrder(request);

                log.info("Order created successfully");

                return ResponseEntity.ok(Map.of(
                    "orderId", "ORD-" + System.currentTimeMillis(),
                    "status", "CREATED",
                    "tenantId", tenantId
                ));
            }
        );
    }

    private void processOrder(Map<String, Object> request) {
        // Tags are still available in nested method calls
        log.atDebug()
            .addKeyValue("amount", request.get("amount"))
            .log("Processing order payment");
    }

    // =========================================================================
    // Example 4: Sensitive Data Masking
    // =========================================================================

    /**
     * Sensitive data is automatically masked in logs.
     *
     * Test: curl -X POST http://localhost:8080/api/users \
     *            -H "Content-Type: application/json" \
     *            -d '{"name":"John Doe","email":"john@example.com","phone":"+33612345678"}'
     *
     * Log output masks email and phone:
     * {
     *   "message": "Creating user: John Doe, email: j***@e***.com, phone: +33 6 ** ** ** 78"
     * }
     */
    @PostMapping("/users")
    public ResponseEntity<Map<String, String>> createUser(@RequestBody Map<String, String> user) {
        String name = user.get("name");
        String email = user.get("email");
        String phone = user.get("phone");

        // Email and phone are automatically masked in log output
        log.info("Creating user: {}, email: {}, phone: {}", name, email, phone);

        // Structured logging with sensitive data
        log.atInfo()
            .addKeyValue("user_name", name)
            .addKeyValue("user_email", email)  // Will be masked
            .addKeyValue("user_phone", phone)  // Will be masked
            .log("User registration processed");

        return ResponseEntity.ok(Map.of(
            "userId", "USR-" + System.currentTimeMillis(),
            "status", "CREATED"
        ));
    }

    // =========================================================================
    // Example 5: Error Logging with Context
    // =========================================================================

    /**
     * Structured error logging with exception context.
     *
     * Test: curl http://localhost:8080/api/error-demo
     *
     * Log output includes structured exception information.
     */
    @GetMapping("/error-demo")
    public ResponseEntity<Map<String, String>> errorDemo() {
        try {
            // Simulate an error
            throw new IllegalStateException("Something went wrong");
        } catch (Exception e) {
            log.atError()
                .addKeyValue("error_code", "ERR-500")
                .addKeyValue("component", "demo_controller")
                .addKeyValue("recoverable", false)
                .setCause(e)
                .log("An error occurred in error-demo endpoint");

            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Internal Server Error",
                "correlationId", CorrelationIdHolder.get()
            ));
        }
    }

    // =========================================================================
    // Example 6: Manual Tag Management
    // =========================================================================

    /**
     * Manual tag management with try-finally for cleanup.
     *
     * Test: curl http://localhost:8080/api/batch/process
     */
    @PostMapping("/batch/process")
    public ResponseEntity<Map<String, String>> processBatch() {
        String batchId = "BATCH-" + System.currentTimeMillis();

        try {
            // Add tags manually
            logMetadataManager
                .addTag("batch_id", batchId)
                .addTag("batch_type", "order_sync");

            log.info("Starting batch processing");

            for (int i = 0; i < 3; i++) {
                logMetadataManager.addTag("batch_item", i);
                log.info("Processing batch item");
                // All logs include batch_id, batch_type, and batch_item
            }

            log.info("Batch processing completed");
            return ResponseEntity.ok(Map.of("batchId", batchId, "status", "COMPLETED"));

        } finally {
            // IMPORTANT: Always clean up tags
            logMetadataManager.removeTags("batch_id", "batch_type", "batch_item");
        }
    }

    // =========================================================================
    // Health Check
    // =========================================================================

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "correlationId", CorrelationIdHolder.get()
        ));
    }
}
