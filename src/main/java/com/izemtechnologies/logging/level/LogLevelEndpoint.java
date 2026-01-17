package com.izemtechnologies.logging.level;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.*;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * Spring Boot Actuator endpoint for managing log levels at runtime.
 * 
 * <p>This endpoint provides a REST API for viewing and modifying log levels
 * without restarting the application.</p>
 * 
 * <h2>Endpoints:</h2>
 * <table>
 *   <tr><th>Method</th><th>Path</th><th>Description</th></tr>
 *   <tr><td>GET</td><td>/actuator/loglevel</td><td>List all configured loggers</td></tr>
 *   <tr><td>GET</td><td>/actuator/loglevel/{name}</td><td>Get level for specific logger</td></tr>
 *   <tr><td>POST</td><td>/actuator/loglevel/{name}</td><td>Set level for specific logger</td></tr>
 *   <tr><td>DELETE</td><td>/actuator/loglevel/{name}</td><td>Reset logger to default</td></tr>
 * </table>
 * 
 * <h2>Example Usage:</h2>
 * <pre>{@code
 * # Get all loggers
 * curl http://localhost:8080/actuator/loglevel
 * 
 * # Get specific logger
 * curl http://localhost:8080/actuator/loglevel/com.example.MyService
 * 
 * # Set level (permanent)
 * curl -X POST http://localhost:8080/actuator/loglevel/com.example.MyService \
 *   -H "Content-Type: application/json" \
 *   -d '{"level": "DEBUG"}'
 * 
 * # Set level (temporary - 15 minutes)
 * curl -X POST http://localhost:8080/actuator/loglevel/com.example.MyService \
 *   -H "Content-Type: application/json" \
 *   -d '{"level": "DEBUG", "durationMinutes": 15}'
 * 
 * # Reset to default
 * curl -X DELETE http://localhost:8080/actuator/loglevel/com.example.MyService
 * }</pre>
 * 
 * @author Souidi
 * @since 1.1.0
 */
@Component
@Endpoint(id = "loglevel")
@RequiredArgsConstructor
public class LogLevelEndpoint {

    private final DynamicLogLevelManager logLevelManager;

    /**
     * Lists all configured loggers and their levels.
     * 
     * @return map of logger names to levels
     */
    @ReadOperation
    public LoggersResponse listLoggers() {
        LoggersResponse response = new LoggersResponse();
        response.setLoggers(logLevelManager.getAllLoggers());
        response.setActiveChanges(logLevelManager.getActiveChanges());
        return response;
    }

    /**
     * Gets the level for a specific logger.
     * 
     * @param name the logger name
     * @return the logger level info
     */
    @ReadOperation
    public LoggerLevelResponse getLoggerLevel(@Selector String name) {
        LoggerLevelResponse response = new LoggerLevelResponse();
        response.setLoggerName(name);
        response.setConfiguredLevel(logLevelManager.getLevel(name));
        response.setEffectiveLevel(logLevelManager.getEffectiveLevel(name));

        DynamicLogLevelManager.LevelChange activeChange = logLevelManager.getActiveChanges().get(name);
        if (activeChange != null) {
            response.setActiveChange(activeChange);
        }

        return response;
    }

    /**
     * Sets the level for a specific logger.
     * 
     * @param name            the logger name
     * @param level           the new log level to set
     * @param durationMinutes optional duration in minutes before reverting
     * @return the result of the operation
     */
    @WriteOperation
    public LoggerLevelResponse setLoggerLevel(@Selector String name, 
                                               @Nullable String level,
                                               @Nullable Integer durationMinutes) {
        Duration duration = durationMinutes != null ? Duration.ofMinutes(durationMinutes) : null;
        String previousLevel = logLevelManager.setLevel(name, level, duration);

        LoggerLevelResponse response = new LoggerLevelResponse();
        response.setLoggerName(name);
        response.setPreviousLevel(previousLevel);
        response.setConfiguredLevel(level);
        response.setEffectiveLevel(logLevelManager.getEffectiveLevel(name));
        response.setTemporary(duration != null);
        response.setDurationMinutes(durationMinutes);

        return response;
    }

    /**
     * Resets a logger to its default level.
     * 
     * @param name the logger name
     * @return confirmation message
     */
    @DeleteOperation
    public Map<String, String> resetLoggerLevel(@Selector String name) {
        logLevelManager.resetLevel(name);
        return Map.of(
            "status", "reset",
            "logger", name,
            "effectiveLevel", logLevelManager.getEffectiveLevel(name)
        );
    }

    // ==================== Response DTOs ====================

    @Data
    public static class LoggersResponse {
        private Map<String, String> loggers;
        private Map<String, DynamicLogLevelManager.LevelChange> activeChanges;
    }

    @Data
    public static class LoggerLevelResponse {
        private String loggerName;
        private String previousLevel;
        private String configuredLevel;
        private String effectiveLevel;
        private boolean temporary;
        private Integer durationMinutes;
        private DynamicLogLevelManager.LevelChange activeChange;
    }
}
