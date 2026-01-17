package com.izemtechnologies.logging.level;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Manager for dynamically changing log levels at runtime.
 * 
 * <p>This component allows changing log levels without restarting the application,
 * which is essential for debugging production issues. It also supports temporary
 * level changes that automatically revert after a specified duration.</p>
 * 
 * <h2>Features:</h2>
 * <ul>
 *   <li>Change log levels at runtime via API</li>
 *   <li>Temporary level changes with auto-revert</li>
 *   <li>Bulk level changes by package prefix</li>
 *   <li>History of level changes for auditing</li>
 *   <li>Integration with Spring Boot Actuator</li>
 * </ul>
 * 
 * @author Souidi
 * @since 1.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicLogLevelManager {

    private final TaskScheduler taskScheduler;

    private final Map<String, LevelChange> activeChanges = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> scheduledReverts = new ConcurrentHashMap<>();
    private final Map<String, LevelChangeHistory> changeHistory = new ConcurrentHashMap<>();

    /**
     * Changes the log level for a specific logger.
     * 
     * @param loggerName the logger name (use "ROOT" for root logger)
     * @param level      the new level
     * @return the previous level
     */
    public String setLevel(String loggerName, String level) {
        return setLevel(loggerName, level, null);
    }

    /**
     * Changes the log level temporarily, reverting after the specified duration.
     * 
     * @param loggerName the logger name
     * @param level      the new level
     * @param duration   duration before reverting (null for permanent change)
     * @return the previous level
     */
    public String setLevel(String loggerName, String level, Duration duration) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger(loggerName);

        Level previousLevel = logger.getLevel();
        Level newLevel = Level.toLevel(level, Level.INFO);

        // Store the change
        LevelChange change = new LevelChange();
        change.setLoggerName(loggerName);
        change.setPreviousLevel(previousLevel != null ? previousLevel.toString() : null);
        change.setNewLevel(newLevel.toString());
        change.setTimestamp(Instant.now());
        change.setDuration(duration);
        change.setTemporary(duration != null);

        activeChanges.put(loggerName, change);

        // Apply the change
        logger.setLevel(newLevel);
        log.info("Changed log level for '{}' from {} to {}{}", 
            loggerName, 
            previousLevel, 
            newLevel,
            duration != null ? " (temporary: " + duration + ")" : "");

        // Schedule revert if temporary
        if (duration != null) {
            scheduleRevert(loggerName, previousLevel, duration);
        }

        // Record in history
        recordHistory(change);

        return previousLevel != null ? previousLevel.toString() : null;
    }

    /**
     * Gets the current log level for a logger.
     * 
     * @param loggerName the logger name
     * @return the current level, or null if not explicitly set
     */
    public String getLevel(String loggerName) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger(loggerName);
        Level level = logger.getLevel();
        return level != null ? level.toString() : null;
    }

    /**
     * Gets the effective log level for a logger (including inherited levels).
     * 
     * @param loggerName the logger name
     * @return the effective level
     */
    public String getEffectiveLevel(String loggerName) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger(loggerName);
        return logger.getEffectiveLevel().toString();
    }

    /**
     * Resets a logger to its default level (removes explicit level setting).
     * 
     * @param loggerName the logger name
     */
    public void resetLevel(String loggerName) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger(loggerName);

        Level previousLevel = logger.getLevel();
        logger.setLevel(null);

        // Cancel any scheduled revert
        cancelScheduledRevert(loggerName);

        // Remove from active changes
        activeChanges.remove(loggerName);

        log.info("Reset log level for '{}' (was: {})", loggerName, previousLevel);
    }

    /**
     * Changes log levels for all loggers matching a package prefix.
     * 
     * @param packagePrefix the package prefix (e.g., "com.example")
     * @param level         the new level
     * @param duration      duration before reverting (null for permanent)
     * @return number of loggers affected
     */
    public int setLevelByPackage(String packagePrefix, String level, Duration duration) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Level newLevel = Level.toLevel(level, Level.INFO);

        int count = 0;
        for (Logger logger : context.getLoggerList()) {
            if (logger.getName().startsWith(packagePrefix)) {
                setLevel(logger.getName(), level, duration);
                count++;
            }
        }

        log.info("Changed log level for {} loggers matching '{}' to {}", count, packagePrefix, newLevel);
        return count;
    }

    /**
     * Gets all active level changes.
     * 
     * @return map of logger name to level change
     */
    public Map<String, LevelChange> getActiveChanges() {
        return new ConcurrentHashMap<>(activeChanges);
    }

    /**
     * Gets all configured loggers and their levels.
     * 
     * @return map of logger name to level
     */
    public Map<String, String> getAllLoggers() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Map<String, String> loggers = new ConcurrentHashMap<>();

        for (Logger logger : context.getLoggerList()) {
            Level level = logger.getLevel();
            if (level != null) {
                loggers.put(logger.getName(), level.toString());
            }
        }

        return loggers;
    }

    /**
     * Enables debug mode for a specific duration.
     * Sets ROOT logger to DEBUG and reverts after the duration.
     * 
     * @param duration the duration
     */
    public void enableDebugMode(Duration duration) {
        setLevel(Logger.ROOT_LOGGER_NAME, "DEBUG", duration);
        log.warn("DEBUG mode enabled for {}. Will auto-revert.", duration);
    }

    /**
     * Enables trace mode for a specific package for debugging.
     * 
     * @param packageName the package to trace
     * @param duration    the duration
     */
    public void enableTraceForPackage(String packageName, Duration duration) {
        setLevel(packageName, "TRACE", duration);
        log.warn("TRACE mode enabled for '{}' for {}. Will auto-revert.", packageName, duration);
    }

    // ==================== Private Methods ====================

    private void scheduleRevert(String loggerName, Level previousLevel, Duration duration) {
        // Cancel any existing scheduled revert
        cancelScheduledRevert(loggerName);

        // Schedule new revert
        ScheduledFuture<?> future = taskScheduler.schedule(
            () -> revertLevel(loggerName, previousLevel),
            Instant.now().plus(duration)
        );

        scheduledReverts.put(loggerName, future);
    }

    private void cancelScheduledRevert(String loggerName) {
        ScheduledFuture<?> existing = scheduledReverts.remove(loggerName);
        if (existing != null) {
            existing.cancel(false);
        }
    }

    private void revertLevel(String loggerName, Level previousLevel) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger(loggerName);

        logger.setLevel(previousLevel);
        activeChanges.remove(loggerName);
        scheduledReverts.remove(loggerName);

        log.info("Auto-reverted log level for '{}' to {}", loggerName, previousLevel);
    }

    private void recordHistory(LevelChange change) {
        LevelChangeHistory history = changeHistory.computeIfAbsent(
            change.getLoggerName(),
            k -> new LevelChangeHistory()
        );
        history.addChange(change);
    }

    // ==================== Inner Classes ====================

    @Data
    public static class LevelChange {
        private String loggerName;
        private String previousLevel;
        private String newLevel;
        private Instant timestamp;
        private Duration duration;
        private boolean temporary;
    }

    @Data
    public static class LevelChangeHistory {
        private final java.util.List<LevelChange> changes = new java.util.ArrayList<>();

        public void addChange(LevelChange change) {
            changes.add(change);
            // Keep only last 100 changes per logger
            if (changes.size() > 100) {
                changes.remove(0);
            }
        }
    }
}
