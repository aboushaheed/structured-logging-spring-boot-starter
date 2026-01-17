package com.example.demo;

import com.izemtechnologies.logging.context.RequestScopedLogContext;
import com.izemtechnologies.logging.correlation.CorrelationIdHolder;
import com.izemtechnologies.logging.level.DynamicLogLevelManager;
import com.izemtechnologies.logging.manager.LogMetadataManager;
import com.izemtechnologies.logging.masking.Sensitive;
import com.izemtechnologies.logging.masking.SensitiveType;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

/**
 * Advanced usage examples demonstrating all features of the logging-starter.
 * 
 * This example shows how to use:
 * - Dynamic tags (global and request-scoped)
 * - Sensitive data masking with @Sensitive annotation
 * - Correlation ID propagation
 * - SLF4J 2.x fluent API
 * - Dynamic log level management
 * - Structured exception logging
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AdvancedUsageExample {

    // Inject the LogMetadataManager for global dynamic tags
    private final LogMetadataManager logMetadataManager;
    
    // Inject for dynamic log level changes
    private final DynamicLogLevelManager logLevelManager;

    // =========================================================================
    // FEATURE 1: Global Dynamic Tags
    // =========================================================================
    
    /**
     * Example: Setting global tags that appear in ALL logs.
     * Useful for cluster identification, feature flags, etc.
     */
    @PostMapping("/config/cluster")
    public ResponseEntity<String> setClusterConfig(@RequestParam String clusterId) {
        // This tag will appear in EVERY log line until removed
        logMetadataManager.addTag("cluster_id", clusterId);
        logMetadataManager.addTag("cluster_configured_at", java.time.Instant.now().toString());
        
        log.info("Cluster configuration updated");
        // Output: {"message":"Cluster configuration updated","cluster_id":"c-123",...}
        
        return ResponseEntity.ok("Cluster configured: " + clusterId);
    }

    /**
     * Example: Using fluent API to add multiple tags at once.
     */
    @PostMapping("/config/environment")
    public ResponseEntity<String> setEnvironmentConfig(
            @RequestParam String env,
            @RequestParam String region) {
        
        logMetadataManager
            .addTag("environment", env)
            .addTag("region", region)
            .addTag("deployment_mode", "blue-green");
        
        log.info("Environment configured");
        return ResponseEntity.ok("Environment set");
    }

    // =========================================================================
    // FEATURE 2: Request-Scoped Tags
    // =========================================================================
    
    /**
     * Example: Tags that only exist for the current request.
     * Perfect for tenant_id, user_id, session_id, etc.
     */
    @GetMapping("/users/{userId}/orders")
    public ResponseEntity<String> getUserOrders(
            @PathVariable String userId,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId) {
        
        // These tags are automatically cleaned up after the request
        RequestScopedLogContext.put("user_id", userId);
        RequestScopedLogContext.put("tenant_id", tenantId != null ? tenantId : "default");
        RequestScopedLogContext.put("operation", "get_orders");
        
        log.info("Fetching orders for user");
        // Output: {"message":"Fetching orders...","user_id":"U123","tenant_id":"T456",...}
        
        // Simulate business logic
        processOrders(userId);
        
        log.info("Orders retrieved successfully");
        return ResponseEntity.ok("Orders for user " + userId);
    }
    
    private void processOrders(String userId) {
        // Tags are still available in nested method calls
        log.debug("Processing orders in business layer");
        // Output still contains user_id and tenant_id
    }

    // =========================================================================
    // FEATURE 3: Correlation ID
    // =========================================================================
    
    /**
     * Example: Accessing the correlation ID for external calls.
     */
    @GetMapping("/external-call")
    public ResponseEntity<Map<String, String>> makeExternalCall() {
        String correlationId = CorrelationIdHolder.get();
        
        log.info("Making external API call");
        // The correlation ID is automatically:
        // 1. Included in logs
        // 2. Propagated via RestTemplate/WebClient interceptors
        // 3. Returned in response headers
        
        return ResponseEntity.ok(Map.of(
            "correlationId", correlationId,
            "status", "External call completed"
        ));
    }

    // =========================================================================
    // FEATURE 4: SLF4J 2.x Fluent API
    // =========================================================================
    
    /**
     * Example: Using SLF4J 2.x fluent API for structured logging.
     * Key-value pairs appear at the JSON root level.
     */
    @PostMapping("/orders")
    public ResponseEntity<String> createOrder(@RequestBody OrderRequest request) {
        // Fluent API - key-values appear at JSON root
        log.atInfo()
            .addKeyValue("order_id", request.getOrderId())
            .addKeyValue("customer_id", request.getCustomerId())
            .addKeyValue("total_amount", request.getTotalAmount())
            .addKeyValue("currency", request.getCurrency())
            .log("Order created successfully");
        
        // Output:
        // {
        //   "message": "Order created successfully",
        //   "order_id": "ORD-123",
        //   "customer_id": "CUST-456",
        //   "total_amount": 99.99,
        //   "currency": "EUR",
        //   ...
        // }
        
        return ResponseEntity.ok("Order created: " + request.getOrderId());
    }
    
    /**
     * Example: Combining fluent API with markers.
     */
    @PostMapping("/payments")
    public ResponseEntity<String> processPayment(@RequestBody PaymentRequest request) {
        log.atInfo()
            .addMarker(org.slf4j.MarkerFactory.getMarker("PAYMENT"))
            .addKeyValue("payment_id", request.getPaymentId())
            .addKeyValue("amount", request.getAmount())
            .addKeyValue("method", request.getMethod())
            .addKeyValue("status", "PROCESSING")
            .log("Payment processing initiated");
        
        // Simulate processing
        try {
            Thread.sleep(100);
            
            log.atInfo()
                .addKeyValue("payment_id", request.getPaymentId())
                .addKeyValue("status", "COMPLETED")
                .addKeyValue("processing_time_ms", 100)
                .log("Payment completed");
                
        } catch (InterruptedException e) {
            log.atError()
                .addKeyValue("payment_id", request.getPaymentId())
                .addKeyValue("status", "FAILED")
                .setCause(e)
                .log("Payment failed");
        }
        
        return ResponseEntity.ok("Payment processed");
    }

    // =========================================================================
    // FEATURE 5: Sensitive Data Masking
    // =========================================================================
    
    /**
     * Example: Automatic masking of sensitive data in logs.
     */
    @PostMapping("/users")
    public ResponseEntity<String> createUser(@RequestBody UserRegistration user) {
        // The SensitiveDataMasker automatically detects and masks:
        // - Email addresses
        // - Phone numbers
        // - Credit card numbers
        // - SSN/NIR
        // - API keys, tokens, passwords
        
        log.info("Creating user with email: {} and phone: {}", 
            user.getEmail(), user.getPhone());
        // Output: "Creating user with email: j***@e***.com and phone: +33 6 ** ** ** 89"
        
        // Using @Sensitive annotation on DTOs
        log.info("User registration data: {}", user);
        // Sensitive fields are automatically masked in toString()
        
        return ResponseEntity.ok("User created");
    }
    
    /**
     * Example: Logging with explicit sensitive data handling.
     */
    @PostMapping("/authenticate")
    public ResponseEntity<String> authenticate(@RequestBody AuthRequest auth) {
        // DON'T log passwords directly - they're auto-masked anyway
        log.info("Authentication attempt for user: {}", auth.getUsername());
        
        // The password field is automatically masked because:
        // 1. Field name contains "password"
        // 2. @Sensitive annotation on the field
        log.debug("Auth request: {}", auth);
        // Output: {"username":"john","password":"***REDACTED***"}
        
        return ResponseEntity.ok("Authenticated");
    }

    // =========================================================================
    // FEATURE 6: Dynamic Log Level Management
    // =========================================================================
    
    /**
     * Example: Changing log levels at runtime for debugging.
     */
    @PostMapping("/debug/enable")
    public ResponseEntity<String> enableDebugMode(
            @RequestParam(defaultValue = "15") int durationMinutes) {
        
        // Enable DEBUG for the entire application temporarily
        logLevelManager.enableDebugMode(Duration.ofMinutes(durationMinutes));
        
        log.info("Debug mode enabled for {} minutes", durationMinutes);
        return ResponseEntity.ok("Debug mode enabled");
    }
    
    /**
     * Example: Enable TRACE for a specific package.
     */
    @PostMapping("/debug/trace")
    public ResponseEntity<String> enableTraceForPackage(
            @RequestParam String packageName,
            @RequestParam(defaultValue = "10") int durationMinutes) {
        
        logLevelManager.enableTraceForPackage(packageName, Duration.ofMinutes(durationMinutes));
        
        log.info("TRACE enabled for package: {}", packageName);
        return ResponseEntity.ok("TRACE enabled for " + packageName);
    }
    
    /**
     * Example: Get current log levels.
     */
    @GetMapping("/debug/levels")
    public ResponseEntity<Map<String, String>> getLogLevels() {
        return ResponseEntity.ok(logLevelManager.getAllLoggers());
    }

    // =========================================================================
    // FEATURE 7: Structured Exception Logging
    // =========================================================================
    
    /**
     * Example: Exceptions are automatically structured in JSON.
     */
    @GetMapping("/error-demo")
    public ResponseEntity<String> demonstrateErrorLogging() {
        try {
            throw new BusinessException("Order not found", "ORD-404", 
                new IllegalStateException("Invalid order state"));
        } catch (BusinessException e) {
            // Exception is automatically structured:
            // {
            //   "exception": {
            //     "type": "com.example.BusinessException",
            //     "message": "Order not found",
            //     "error_code": "ORD-404",
            //     "stack_hash": "a1b2c3d4",
            //     "root_cause": {
            //       "type": "java.lang.IllegalStateException",
            //       "message": "Invalid order state"
            //     },
            //     "stack_trace": [...]
            //   }
            // }
            log.error("Business error occurred", e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    // =========================================================================
    // DTOs with Sensitive Data Annotations
    // =========================================================================
    
    @Data
    public static class UserRegistration {
        private String username;
        
        @Sensitive(type = SensitiveType.EMAIL)
        private String email;
        
        @Sensitive(type = SensitiveType.PHONE)
        private String phone;
        
        @Sensitive(type = SensitiveType.PASSWORD)
        private String password;
        
        @Sensitive(type = SensitiveType.CREDIT_CARD)
        private String creditCard;
        
        @Sensitive(type = SensitiveType.SSN)
        private String ssn;
    }
    
    @Data
    public static class AuthRequest {
        private String username;
        
        @Sensitive(type = SensitiveType.PASSWORD)
        private String password;
        
        @Sensitive(type = SensitiveType.JWT)
        private String refreshToken;
    }
    
    @Data
    public static class OrderRequest {
        private String orderId;
        private String customerId;
        private Double totalAmount;
        private String currency;
    }
    
    @Data
    public static class PaymentRequest {
        private String paymentId;
        private Double amount;
        private String method;
        
        @Sensitive(type = SensitiveType.CREDIT_CARD)
        private String cardNumber;
    }
    
    // Custom exception with additional context
    public static class BusinessException extends RuntimeException {
        private final String errorCode;
        
        public BusinessException(String message, String errorCode, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }
        
        public String getErrorCode() {
            return errorCode;
        }
    }
}

// =========================================================================
// EXAMPLE: Custom Log Enricher
// =========================================================================

/**
 * Example of a custom enricher that adds business context to all logs.
 */
@Slf4j
@org.springframework.stereotype.Component
class TenantContextEnricher implements com.izemtechnologies.logging.enrichment.LogEnricher {
    
    @Override
    public java.util.Map<String, Object> enrich() {
        // Get tenant from security context or thread local
        String tenantId = RequestScopedLogContext.get("tenant_id");
        if (tenantId != null) {
            return java.util.Map.of(
                "tenant.id", tenantId,
                "tenant.tier", getTenantTier(tenantId)
            );
        }
        return java.util.Map.of();
    }
    
    private String getTenantTier(String tenantId) {
        // Lookup tenant tier from cache/database
        return "enterprise";
    }
    
    @Override
    public int getOrder() {
        return 200; // Run after default enrichers
    }
    
    @Override
    public String getName() {
        return "TenantContextEnricher";
    }
}

// =========================================================================
// EXAMPLE: Using with Async/Scheduled Tasks
// =========================================================================

/**
 * Example showing correlation ID propagation in async tasks.
 */
@Slf4j
@org.springframework.stereotype.Service
@RequiredArgsConstructor
class AsyncTaskService {
    
    private final org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor taskExecutor;
    private final com.izemtechnologies.logging.correlation.CorrelationIdTaskDecorator taskDecorator;
    
    @jakarta.annotation.PostConstruct
    public void init() {
        // Configure task executor with correlation ID propagation
        taskExecutor.setTaskDecorator(taskDecorator);
    }
    
    public void processAsync(String data) {
        String correlationId = CorrelationIdHolder.get();
        log.info("Starting async processing");
        
        taskExecutor.execute(() -> {
            // Correlation ID is automatically propagated!
            log.info("Processing data in async thread");
            // Output still contains the same correlationId
        });
    }
}
