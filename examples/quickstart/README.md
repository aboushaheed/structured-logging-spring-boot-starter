# Quickstart Example

This is a complete, runnable Spring Boot project demonstrating how to integrate the `structured-logging-spring-boot-starter`.

**GitHub Repository:** https://github.com/aboushaheed/structured-logging-spring-boot-starter

## Project Structure

```
quickstart/
├── pom.xml                           # Maven config with logging-starter dependency
├── README.md                         # This file
└── src/main/
    ├── java/com/example/quickstart/
    │   ├── QuickstartApplication.java    # Main application
    │   └── DemoController.java           # Demo endpoints
    └── resources/
        └── application.yaml              # Logging configuration
```

## Prerequisites

- Java 17+
- Maven 3.6+
- GitHub account (for accessing GitHub Packages)

## Setup GitHub Packages Authentication

The library is hosted on GitHub Packages. Before running, configure Maven authentication:

**1. Create a GitHub Personal Access Token (PAT):**
   - Go to https://github.com/settings/tokens
   - Generate a new token with `read:packages` scope

**2. Add credentials to `~/.m2/settings.xml`:**

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

## Run the Application

```bash
# From the quickstart directory
mvn spring-boot:run
```

## Test the Endpoints

### 1. Basic Logging
```bash
curl http://localhost:8080/api/hello
```

Log output:
```json
{
  "@timestamp": "2024-01-15T10:30:45.123Z",
  "level": "INFO",
  "message": "Hello endpoint called",
  "application": "logging-quickstart",
  "env": "development",
  "correlationId": "1705312245000-a1b2c3d4"
}
```

### 2. With Correlation ID
```bash
curl -H "X-Correlation-ID: my-trace-123" http://localhost:8080/api/hello
```

### 3. SLF4J 2.x Fluent API
```bash
curl http://localhost:8080/api/orders/ORD-123
```

Log output includes `order_id` and `operation` at JSON root level.

### 4. Dynamic Tags with Tenant Context
```bash
curl -X POST http://localhost:8080/api/orders \
     -H "Content-Type: application/json" \
     -H "X-Tenant-ID: tenant-123" \
     -d '{"customerId":"CUST-456","amount":99.99}'
```

### 5. Sensitive Data Masking
```bash
curl -X POST http://localhost:8080/api/users \
     -H "Content-Type: application/json" \
     -d '{"name":"John Doe","email":"john@example.com","phone":"+33612345678"}'
```

Log output shows masked email and phone.

### 6. Error Logging
```bash
curl http://localhost:8080/api/error-demo
```

### 7. Batch Processing with Tags
```bash
curl -X POST http://localhost:8080/api/batch/process
```

## Configuration Highlights

See `src/main/resources/application.yaml` for the logging configuration:

- **JSON output**: Enabled with pretty-print for development
- **Correlation ID**: Auto-generated and propagated
- **Static tags**: `env` and `version` included in every log
- **Masking**: Email, phone, credit cards, passwords auto-masked

## What's Included Automatically

Once you add the dependency, you get:

1. **Structured JSON logs** - No logback.xml needed
2. **Correlation ID** - Auto-generated, propagated across services
3. **MDC context** - Thread-local context in logs
4. **Sensitive data masking** - GDPR/PCI-DSS compliant
5. **SLF4J 2.x support** - Fluent API with key-value pairs
6. **Health indicator** - Logging subsystem health at `/actuator/health`
