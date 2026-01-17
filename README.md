# Structured Logging Spring Boot Starter

[![Maven Central](https://img.shields.io/maven-central/v/com.izemtechnologies/structured-logging-spring-boot-starter.svg)](https://search.maven.org/artifact/com.izemtechnologies/structured-logging-spring-boot-starter)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2%2B-green.svg)](https://spring.io/projects/spring-boot)

A production-grade Spring Boot Starter for **structured JSON logging**. This library transforms all your application logs into a unified JSON format, making them easily searchable and analyzable in tools like Elasticsearch, Splunk, or Datadog.

**Developed by [Izem Technologies](https://izemtechnologies.com) | Author: Souidi**

---

## Table of Contents

1. [Why Use This Library?](#why-use-this-library)
2. [Installation](#installation)
3. [Quick Start (5 Minutes)](#quick-start-5-minutes)
4. [Expected Output Examples](#expected-output-examples)
5. [Feature Guide](#feature-guide)
6. [Complete Configuration Reference](#complete-configuration-reference)
7. [Troubleshooting](#troubleshooting)

---

## Why Use This Library?

| Problem | Solution |
|---------|----------|
| Logs are unstructured text, hard to search | All logs become structured JSON |
| Sensitive data leaks into logs (GDPR violation) | Automatic masking of emails, passwords, tokens |
| Can't trace requests across microservices | Automatic Correlation ID propagation |
| Too many logs in production, high costs | Intelligent sampling reduces volume |
| Need to add context to logs manually | Dynamic tags added globally or per-request |
| XML configuration is complex | Zero XML - everything via `application.yaml` |

---

## Installation

### Prerequisites

- **Java 17** or higher
- **Spring Boot 3.2** or higher

### Maven

Add this single dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.izemtechnologies</groupId>
    <artifactId>structured-logging-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

Add to your `build.gradle`:

```groovy
implementation 'com.izemtechnologies:structured-logging-spring-boot-starter:1.0.0'
```

**That's it!** The library auto-configures itself. No `logback.xml` needed.

---

## Quick Start (5 Minutes)

### Step 1: Add the Dependency

Add the Maven or Gradle dependency shown above.

### Step 2: Add Basic Configuration

Create or update your `application.yaml`:

```yaml
app:
  logging:
    enabled: true
    application-name: my-awesome-service
    
    # Tags included in EVERY log line
    static-tags:
      env: ${ENVIRONMENT:development}
      version: ${APP_VERSION:1.0.0}
      team: backend
```

### Step 3: Use in Your Code

```java
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @PostMapping
    public Order createOrder(@RequestBody OrderRequest request) {
        
        // Simple logging - automatically becomes JSON
        log.info("Received order request");
        
        // Logging with key-value pairs (SLF4J 2.x fluent API)
        log.atInfo()
            .addKeyValue("order_id", request.getId())
            .addKeyValue("customer_id", request.getCustomerId())
            .addKeyValue("amount", request.getAmount())
            .log("Processing order");
        
        return orderService.create(request);
    }
}
```

### Step 4: Run Your Application

Start your Spring Boot application. All logs will now be JSON formatted.

---

## Expected Output Examples

### Basic Log Output

**Your code:**
```java
log.info("Application started successfully");
```

**JSON output:**
```json
{
  "@timestamp": "2024-01-15T10:30:45.123Z",
  "level": "INFO",
  "logger": "com.example.MyApplication",
  "message": "Application started successfully",
  "thread": "main",
  "application": "my-awesome-service",
  "env": "development",
  "version": "1.0.0",
  "team": "backend"
}
```

### Log with Key-Value Pairs

**Your code:**
```java
log.atInfo()
    .addKeyValue("order_id", "ORD-12345")
    .addKeyValue("customer_id", "CUST-67890")
    .addKeyValue("amount", 299.99)
    .addKeyValue("currency", "EUR")
    .log("Order processed successfully");
```

**JSON output:**
```json
{
  "@timestamp": "2024-01-15T10:31:22.456Z",
  "level": "INFO",
  "logger": "com.example.OrderService",
  "message": "Order processed successfully",
  "thread": "http-nio-8080-exec-1",
  "order_id": "ORD-12345",
  "customer_id": "CUST-67890",
  "amount": 299.99,
  "currency": "EUR",
  "application": "my-awesome-service",
  "env": "development",
  "version": "1.0.0",
  "team": "backend"
}
```

### Log with Exception

**Your code:**
```java
try {
    orderService.process(order);
} catch (Exception e) {
    log.error("Failed to process order", e);
}
```

**JSON output:**
```json
{
  "@timestamp": "2024-01-15T10:32:00.789Z",
  "level": "ERROR",
  "logger": "com.example.OrderService",
  "message": "Failed to process order",
  "thread": "http-nio-8080-exec-1",
  "exception": {
    "type": "com.example.InsufficientInventoryException",
    "message": "Not enough stock for product SKU-123",
    "stack_hash": "a1b2c3d4e5f6",
    "root_cause": {
      "type": "java.sql.SQLException",
      "message": "Connection refused"
    },
    "stack_trace": [
      {"class": "InventoryService", "method": "checkStock", "line": 45},
      {"class": "OrderService", "method": "process", "line": 78}
    ]
  },
  "application": "my-awesome-service",
  "env": "development"
}
```

### HTTP Request/Response Log (Logbook)

When an HTTP request is made to your API:

**JSON output:**
```json
{
  "@timestamp": "2024-01-15T10:33:15.000Z",
  "level": "INFO",
  "logger": "http.traffic",
  "message": "Incoming request",
  "http": {
    "type": "request",
    "method": "POST",
    "uri": "/api/orders",
    "headers": {
      "Content-Type": "application/json",
      "Authorization": "***MASKED***"
    },
    "body": {
      "customerId": "CUST-123",
      "items": [{"sku": "PROD-1", "quantity": 2}],
      "email": "j***@e***.com"
    }
  },
  "correlationId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### Sensitive Data Masking

**Your code:**
```java
log.atInfo()
    .addKeyValue("email", "john.doe@example.com")
    .addKeyValue("credit_card", "4111111111111111")
    .addKeyValue("password", "secret123")
    .addKeyValue("api_key", "sk_live_abc123xyz789")
    .log("User data received");
```

**JSON output (data automatically masked):**
```json
{
  "@timestamp": "2024-01-15T10:34:00.000Z",
  "level": "INFO",
  "message": "User data received",
  "email": "j***.d**@e*****.com",
  "credit_card": "4111 **** **** 1111",
  "password": "***REDACTED***",
  "api_key": "sk_***789"
}
```

---

## Feature Guide

### 1. Dynamic Runtime Tags

Add tags that appear in ALL subsequent logs:

```java
import com.izemtechnologies.logging.manager.LogMetadataManager;

@Service
@RequiredArgsConstructor
public class TenantService {
    
    private final LogMetadataManager logMetadataManager;
    
    public void setCurrentTenant(String tenantId, String tenantName) {
        // These tags will appear in ALL logs from now on
        logMetadataManager
            .addTag("tenant_id", tenantId)
            .addTag("tenant_name", tenantName);
    }
    
    public void clearTenant() {
        logMetadataManager
            .removeTag("tenant_id")
            .removeTag("tenant_name");
    }
}
```

**All subsequent logs will include:**
```json
{
  "message": "Any log message",
  "tenant_id": "TENANT-123",
  "tenant_name": "Acme Corp",
  "...": "..."
}
```

### 2. Request-Scoped Tags

Tags that exist only for the current HTTP request (automatically cleaned up):

```java
import com.izemtechnologies.logging.context.RequestScopedLogContext;

@RestController
public class UserController {
    
    @GetMapping("/users/{userId}")
    public User getUser(@PathVariable String userId, HttpServletRequest request) {
        // These tags exist ONLY for this request
        RequestScopedLogContext.put("user_id", userId);
        RequestScopedLogContext.put("request_ip", request.getRemoteAddr());
        RequestScopedLogContext.put("operation", "get_user");
        
        log.info("Fetching user details");
        // Output includes: user_id, request_ip, operation
        
        User user = userService.findById(userId);
        
        log.info("User found");
        // Output still includes: user_id, request_ip, operation
        
        return user;
        // Tags are automatically removed after response
    }
}
```

### 3. Correlation ID for Distributed Tracing

Automatically tracks requests across microservices:

```yaml
app:
  logging:
    correlation:
      enabled: true
      header-name: X-Correlation-ID
      generate-if-missing: true
```

**How it works:**

| Scenario | Behavior |
|----------|----------|
| Incoming request has `X-Correlation-ID` header | Uses that ID for all logs |
| Incoming request has no header | Generates new UUID automatically |
| Outgoing RestTemplate call | Automatically adds header |
| Outgoing WebClient call | Automatically adds header |
| Async task (@Async) | Propagates ID to new thread |

**All logs include:**
```json
{
  "message": "Processing request",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### 4. GDPR-Compliant Data Masking

Automatically detects and masks sensitive data:

```yaml
app:
  logging:
    masking:
      enabled: true
      default-mode: PARTIAL  # FULL, PARTIAL, HASH, or REDACT
      enabled-types:
        - EMAIL
        - PHONE
        - CREDIT_CARD
        - IBAN
        - PASSWORD
        - JWT
        - API_KEY
        - AWS_ACCESS_KEY
        - VAULT_TOKEN
```

**Masking modes:**

| Mode | Input | Output |
|------|-------|--------|
| `PARTIAL` | john@example.com | j***@e***.com |
| `FULL` | john@example.com | **************** |
| `HASH` | john@example.com | a1b2c3d4e5f6... (SHA-256) |
| `REDACT` | john@example.com | ***REDACTED*** |

### 5. Log Sampling (Reduce Costs)

Reduce log volume in high-traffic environments:

```yaml
app:
  logging:
    sampling:
      enabled: true
      strategy: RATE        # RATE, COUNT_BASED, or ADAPTIVE
      rate: 0.1             # Log only 10% of messages
      always-sample-errors: true  # Always log ERROR level
      
      # Different rates for different paths
      path-rates:
        "/health": 0.01     # Only 1% of health checks
        "/api/search": 0.5  # 50% of search requests
```

### 6. Dynamic Log Level Changes

Change log levels at runtime without restart:

```java
import com.izemtechnologies.logging.level.DynamicLogLevelManager;

@Service
@RequiredArgsConstructor
public class DebugService {
    
    private final DynamicLogLevelManager logLevelManager;
    
    public void enableDebugForInvestigation() {
        // Enable DEBUG for 15 minutes, then auto-revert
        logLevelManager.setLevel(
            "com.example.problematic.Service",
            Level.DEBUG,
            Duration.ofMinutes(15)
        );
    }
}
```

**Or via Actuator endpoint:**
```bash
curl -X POST "http://localhost:8080/actuator/loglevel" \
  -H "Content-Type: application/json" \
  -d '{
    "logger": "com.example.MyService",
    "level": "DEBUG",
    "durationMinutes": 15
  }'
```

---

## Complete Configuration Reference

```yaml
app:
  logging:
    # Master switch
    enabled: true
    application-name: my-service
    
    # Static tags (included in every log)
    static-tags:
      env: ${ENVIRONMENT:development}
      version: ${APP_VERSION:1.0.0}
      region: ${AWS_REGION:eu-west-1}
    
    # JSON output configuration
    json:
      enabled: true
      pretty-print: false
      timestamp-format: "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
      timezone: UTC
      include-mdc: true
      include-caller-data: false
    
    # Correlation ID
    correlation:
      enabled: true
      header-name: X-Correlation-ID
      generate-if-missing: true
      exclude-paths:
        - /actuator/**
        - /health/**
    
    # Data masking (GDPR)
    masking:
      enabled: true
      default-mode: PARTIAL
      mask-personal-data: true
      mask-secrets: true
      enabled-types:
        - EMAIL
        - PHONE
        - CREDIT_CARD
        - SSN
        - PASSWORD
        - JWT
        - API_KEY
        - IBAN
    
    # Log sampling
    sampling:
      enabled: false
      strategy: RATE
      rate: 1.0
      always-sample-errors: true
    
    # Async buffer
    async:
      enabled: true
      buffer-size: 8192
      backpressure-strategy: DROP_LOW_PRIORITY
    
    # Exception formatting
    exception:
      structured: true
      max-stack-frames: 30
      include-stack-hash: true
    
    # Log enrichment
    enrichment:
      enabled: true
      include-application: true
      include-host: true
      include-runtime: true
      include-git: true
    
    # HTTP traffic logging (Logbook)
    logbook:
      enabled: true
      log-request-body: true
      log-response-body: true
      max-body-size: 8192
      masked-headers:
        - Authorization
        - X-Api-Key
        - Cookie
      exclude-paths:
        - /actuator/**
        - /health/**
    
    # Dynamic log levels
    dynamic-level:
      enabled: true
      expose-endpoint: true
      default-duration-minutes: 15
    
    # Console output
    console:
      enabled: true
      level: DEBUG
    
    # File output
    file:
      enabled: false
      path: logs/application.log
      level: INFO
      max-size: 100MB
      max-history: 30
```

---

## Troubleshooting

### Logs are not in JSON format

**Cause:** Another logging configuration is overriding this library.

**Solution:** Remove any `logback.xml` or `logback-spring.xml` from your project.

### Sensitive data is not being masked

**Cause:** The field name doesn't match known patterns.

**Solution:** Add custom patterns in configuration:
```yaml
app:
  logging:
    masking:
      masked-fields:
        - my_custom_secret_field
        - another_sensitive_field
```

### Correlation ID not propagating to async tasks

**Cause:** Default executor doesn't propagate context.

**Solution:** The library auto-configures this, but ensure you're using `@Async` annotation properly:
```java
@Async
public CompletableFuture<Result> asyncMethod() {
    // Correlation ID is automatically available here
    log.info("Async processing");
    return CompletableFuture.completedFuture(result);
}
```

### High memory usage with async logging

**Solution:** Adjust buffer size and backpressure strategy:
```yaml
app:
  logging:
    async:
      buffer-size: 4096
      backpressure-strategy: SAMPLE  # or DROP_LOW_PRIORITY
```

---

## Building from Source

```bash
git clone https://github.com/izemtechnologies/structured-logging-spring-boot-starter.git
cd structured-logging-spring-boot-starter
mvn clean install
```

## Publishing to Maven Central

```bash
mvn clean deploy -P release
```

---

## License

Apache License 2.0

## Support

- **GitHub Issues**: [Report a bug or request a feature](https://github.com/izemtechnologies/structured-logging-spring-boot-starter/issues)
- **Website**: [https://izemtechnologies.com](https://izemtechnologies.com)
- **Email**: contact@izemtechnologies.com

---

**Author:** Souidi  
**Organization:** [Izem Technologies](https://izemtechnologies.com)

Made with ❤️ by Izem Technologies
