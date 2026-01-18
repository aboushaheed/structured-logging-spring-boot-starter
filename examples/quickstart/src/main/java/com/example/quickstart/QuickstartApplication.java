package com.example.quickstart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Quickstart Example for Structured JSON Logging.
 *
 * This example demonstrates how to integrate the logging-starter
 * into an existing Spring Boot application.
 *
 * Run the application and test with:
 *   curl http://localhost:8080/api/hello
 *   curl -H "X-Correlation-ID: my-trace-123" http://localhost:8080/api/hello
 *   curl -X POST http://localhost:8080/api/users -H "Content-Type: application/json" \
 *        -d '{"name":"John","email":"john@example.com"}'
 */
@SpringBootApplication
public class QuickstartApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuickstartApplication.class, args);
    }
}
