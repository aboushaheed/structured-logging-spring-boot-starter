package com.izemtechnologies.logging.routing;

import ch.qos.logback.classic.Level;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Defines a routing rule for directing logs to specific destinations.
 * 
 * <p>Routing rules can filter logs based on level, logger name, markers,
 * and custom conditions. Multiple destinations can be specified per rule.</p>
 * 
 * @author Souidi
 * @since 1.1.0
 */
@Data
@Builder
public class RoutingRule {

    /**
     * Unique name for this rule.
     */
    private String name;

    /**
     * Description of the rule's purpose.
     */
    private String description;

    /**
     * Whether this rule is enabled.
     */
    @Builder.Default
    private boolean enabled = true;

    /**
     * Priority (lower = higher priority, evaluated first).
     */
    @Builder.Default
    private int priority = 100;

    /**
     * Minimum log level to match.
     */
    private Level minLevel;

    /**
     * Maximum log level to match.
     */
    private Level maxLevel;

    /**
     * Specific levels to match (if set, overrides min/max).
     */
    private Set<Level> levels;

    /**
     * Logger name patterns to match (supports wildcards).
     */
    private List<String> loggerPatterns;

    /**
     * Compiled logger patterns for efficient matching.
     */
    private transient List<Pattern> compiledLoggerPatterns;

    /**
     * Marker names to match.
     */
    private Set<String> markers;

    /**
     * MDC keys that must be present.
     */
    private Set<String> requiredMdcKeys;

    /**
     * MDC key-value pairs that must match.
     */
    private java.util.Map<String, String> mdcMatches;

    /**
     * Message patterns to match (regex).
     */
    private List<String> messagePatterns;

    /**
     * Compiled message patterns.
     */
    private transient List<Pattern> compiledMessagePatterns;

    /**
     * Exception types to match.
     */
    private Set<String> exceptionTypes;

    /**
     * Destinations to route matching logs to.
     */
    private List<LogDestination> destinations;

    /**
     * Whether to stop processing further rules after this one matches.
     */
    @Builder.Default
    private boolean terminal = false;

    /**
     * Custom condition expression (SpEL).
     */
    private String condition;

    /**
     * Tags to add to logs matching this rule.
     */
    private java.util.Map<String, String> addTags;

    /**
     * Compiles string patterns to regex patterns.
     */
    public void compile() {
        if (loggerPatterns != null && !loggerPatterns.isEmpty()) {
            compiledLoggerPatterns = loggerPatterns.stream()
                .map(this::wildcardToRegex)
                .map(Pattern::compile)
                .toList();
        }

        if (messagePatterns != null && !messagePatterns.isEmpty()) {
            compiledMessagePatterns = messagePatterns.stream()
                .map(Pattern::compile)
                .toList();
        }
    }

    private String wildcardToRegex(String wildcard) {
        return wildcard
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".");
    }

    /**
     * Checks if a logger name matches this rule's patterns.
     */
    public boolean matchesLogger(String loggerName) {
        if (compiledLoggerPatterns == null || compiledLoggerPatterns.isEmpty()) {
            return true;
        }
        return compiledLoggerPatterns.stream()
            .anyMatch(p -> p.matcher(loggerName).matches());
    }

    /**
     * Checks if a message matches this rule's patterns.
     */
    public boolean matchesMessage(String message) {
        if (compiledMessagePatterns == null || compiledMessagePatterns.isEmpty()) {
            return true;
        }
        return compiledMessagePatterns.stream()
            .anyMatch(p -> p.matcher(message).find());
    }

    /**
     * Checks if a level matches this rule.
     */
    public boolean matchesLevel(Level level) {
        if (levels != null && !levels.isEmpty()) {
            return levels.contains(level);
        }
        if (minLevel != null && level.toInt() < minLevel.toInt()) {
            return false;
        }
        if (maxLevel != null && level.toInt() > maxLevel.toInt()) {
            return false;
        }
        return true;
    }
}
