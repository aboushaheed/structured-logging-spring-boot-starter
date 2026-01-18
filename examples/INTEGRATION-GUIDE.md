# Integration Guide: Adding Structured Logging to Your Spring Boot Project

This guide shows you how to integrate the `structured-logging-spring-boot-starter` into an existing Spring Boot application in 5 minutes.

**GitHub Repository:** https://github.com/aboushaheed/structured-logging-spring-boot-starter

---

## Step 1: Configure GitHub Packages Repository

The library is hosted on GitHub Packages. You need to configure your project to access it.

### Maven

**1. Add the repository to your `pom.xml`:**

```xml
<repositories>
    <repository>
        <id>github</id>
        <name>GitHub Packages - Structured Logging Starter</name>
        <url>https://maven.pkg.github.com/aboushaheed/structured-logging-spring-boot-starter</url>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>
```

**2. Configure authentication in `~/.m2/settings.xml`:**

```xml
<settings>
    <servers>
        <server>
            <id>github</id>
            <username>YOUR_GITHUB_USERNAME</username>
            <password>YOUR_GITHUB_TOKEN</password>
        </server>
    </servers>
</settings>
```

> **Note:** Generate a GitHub Personal Access Token (PAT) with `read:packages` scope at:
> https://github.com/settings/tokens

**3. Add the dependency to your `pom.xml`:**

```xml
<dependency>
    <groupId>com.izemtechnologies</groupId>
    <artifactId>structured-logging-spring-boot-starter</artifactId>
    <version>1.0.1</version>
</dependency>
```

### Gradle

**1. Add the repository to your `build.gradle`:**

```groovy
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/aboushaheed/structured-logging-spring-boot-starter")
        credentials {
            username = project.findProperty("gpr.user") ?: System.getenv("GITHUB_USERNAME")
            password = project.findProperty("gpr.token") ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
```

**2. Add the dependency:**

```groovy
implementation 'com.izemtechnologies:structured-logging-spring-boot-starter:1.0.1'
```

**3. Configure credentials in `~/.gradle/gradle.properties`:**

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.token=YOUR_GITHUB_TOKEN
```

---

## Step 2: Remove Existing Logback Configuration (Optional but Recommended)

If you have a custom `logback.xml` or `logback-spring.xml`, the library will not override it.

**For best results:** Remove or rename your existing logback configuration files:

```bash
# Backup and remove
mv src/main/resources/logback.xml src/main/resources/logback.xml.backup
mv src/main/resources/logback-spring.xml src/main/resources/logback-spring.xml.backup
```

---

## Step 3: Add Minimal Configuration

Create or update your `application.yaml`:

```yaml
app:
  logging:
    enabled: true
    application-name: ${spring.application.name:my-service}

    # Tags included in EVERY log line
    static-tags:
      env: ${ENVIRONMENT:development}
      version: ${APP_VERSION:1.0.0}
```

**That's it!** Your application now logs in structured JSON format.

---

## Step 4: Verify It Works

Start your application and you'll see JSON logs:

```json
{
  "@timestamp": "2024-01-15T10:30:45.123Z",
  "level": "INFO",
  "logger": "com.example.MyApplication",
  "message": "Started MyApplication in 2.5 seconds",
  "thread": "main",
  "application": "my-service",
  "env": "development",
  "version": "1.0.0"
}
```

---

## Step 5: Start Using the Features

### A. Basic Logging (No Changes Needed)

Your existing `log.info()` calls automatically become JSON:

```java
log.info("User logged in");
```

Output:
```json
{"@timestamp":"...","level":"INFO","message":"User logged in","application":"my-service",...}
```

### B. Add Key-Value Pairs (SLF4J 2.x Fluent API)

```java
log.atInfo()
    .addKeyValue("user_id", userId)
    .addKeyValue("action", "login")
    .log("User logged in");
```

Output:
```json
{"message":"User logged in","user_id":"U123","action":"login",...}
```

### C. Use Dynamic Tags (Inject LogMetadataManager)

```java
@Service
@RequiredArgsConstructor
public class MyService {

    private final LogMetadataManager logMetadataManager;

    public void setTenant(String tenantId) {
        logMetadataManager.addTag("tenant_id", tenantId);
        // All subsequent logs will include tenant_id
    }
}
```

### D. Request-Scoped Tags (Auto-Cleanup)

```java
@GetMapping("/users/{id}")
public User getUser(@PathVariable String id) {
    RequestScopedLogContext.put("user_id", id);
    RequestScopedLogContext.put("operation", "get_user");

    log.info("Fetching user");  // Includes user_id and operation
    return userService.findById(id);
    // Tags are automatically removed after the request
}
```

---

## Common Integration Scenarios

### Scenario 1: Existing Spring Boot REST API

```java
// Before (plain text logs)
@RestController
public class OrderController {
    @PostMapping("/orders")
    public Order createOrder(@RequestBody OrderRequest request) {
        log.info("Creating order for customer: " + request.getCustomerId());
        return orderService.create(request);
    }
}

// After (structured JSON logs with context)
@RestController
@RequiredArgsConstructor
public class OrderController {

    private final LogMetadataManager logMetadataManager;

    @PostMapping("/orders")
    public Order createOrder(@RequestBody OrderRequest request,
                             @RequestHeader("X-Tenant-Id") String tenantId) {

        return logMetadataManager.withTags(
            Map.of("tenant_id", tenantId, "customer_id", request.getCustomerId()),
            () -> {
                log.atInfo()
                    .addKeyValue("order_type", request.getType())
                    .addKeyValue("item_count", request.getItems().size())
                    .log("Creating order");

                return orderService.create(request);
            }
        );
    }
}
```

### Scenario 2: Background/Batch Jobs

```java
@Component
@RequiredArgsConstructor
public class OrderSyncJob {

    private final LogMetadataManager logMetadataManager;

    @Scheduled(cron = "0 0 * * * *")
    public void syncOrders() {
        String jobId = UUID.randomUUID().toString();

        logMetadataManager
            .addTag("job_id", jobId)
            .addTag("job_type", "order_sync");

        try {
            log.info("Starting order sync job");
            // Process orders...
            log.info("Order sync completed");
        } finally {
            logMetadataManager.removeTags("job_id", "job_type");
        }
    }
}
```

### Scenario 3: Microservices with Correlation ID

The library automatically:
1. Extracts `X-Correlation-ID` from incoming requests
2. Generates one if missing
3. Includes it in all logs
4. Propagates it to outgoing REST calls

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final RestTemplate restTemplate;  // Auto-configured with interceptor

    public void processOrder(String orderId) {
        log.info("Processing order");
        // Log includes correlationId automatically

        // Call another service - correlationId is automatically propagated
        restTemplate.getForObject("http://inventory-service/check/{id}",
            InventoryStatus.class, orderId);
    }
}
```

### Scenario 4: Multi-Tenant SaaS Application

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantFilter implements Filter {

    @Autowired
    private LogMetadataManager logMetadataManager;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String tenantId = httpRequest.getHeader("X-Tenant-ID");

        if (tenantId != null) {
            logMetadataManager.addTag("tenant_id", tenantId);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            logMetadataManager.removeTag("tenant_id");
        }
    }
}
```

---

## Recommended Configuration for Production

```yaml
app:
  logging:
    enabled: true
    application-name: ${spring.application.name}

    static-tags:
      env: ${ENVIRONMENT:production}
      version: ${APP_VERSION:unknown}
      region: ${AWS_REGION:eu-west-1}

    # JSON output settings
    json:
      enabled: true
      pretty-print: false          # Keep false for production
      include-mdc: true
      include-caller-data: false   # Keep false for performance

    # Correlation ID for distributed tracing
    correlation:
      enabled: true
      header-name: X-Correlation-ID
      generate-if-missing: true

    # GDPR-compliant data masking
    masking:
      enabled: true
      default-mode: PARTIAL
      enabled-types:
        - EMAIL
        - PHONE
        - CREDIT_CARD
        - PASSWORD
        - JWT
        - API_KEY

    # Reduce log volume in production
    sampling:
      enabled: true
      strategy: ADAPTIVE
      rate: 0.5                    # Log 50% of DEBUG/INFO
      always-sample-errors: true   # Always log ERROR/WARN

    # Console output
    console:
      enabled: true
      level: INFO
```

---

## Troubleshooting

### Logs are still in plain text format

1. Check if you have a `logback.xml` or `logback-spring.xml` file - remove it
2. Verify the dependency is correctly added: `mvn dependency:tree | grep logging`
3. Check that `app.logging.enabled: true` in your configuration

### Sensitive data is not being masked

1. Verify masking is enabled: `app.logging.masking.enabled: true`
2. Check the field name matches known patterns (email, password, token, etc.)
3. Add custom field names:
   ```yaml
   app:
     logging:
       masking:
         masked-fields:
           - my_secret_field
           - another_sensitive_field
   ```

### Correlation ID not appearing in logs

1. Verify correlation is enabled: `app.logging.correlation.enabled: true`
2. Check MDC is included: `app.logging.json.include-mdc: true`
3. Ensure the request goes through the filter chain (not excluded path)

---

## Next Steps

- See `UsageExample.java` for basic usage patterns
- See `AdvancedUsageExample.java` for advanced features
- See `application-complete.yaml` for all configuration options
