package com.izemtechnologies.logging.routing;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import com.izemtechnologies.logging.properties.LoggingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Marker;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes log events to appropriate destinations based on configured rules.
 * 
 * <p>The router evaluates each log event against configured rules and determines
 * which destinations should receive the event. Rules are evaluated in priority
 * order, and multiple rules can match the same event.</p>
 * 
 * @author Souidi
 * @since 1.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogRouter {

    private final LoggingProperties properties;

    private final List<RoutingRule> rules = new ArrayList<>();
    private final Map<String, RoutingRule> rulesByName = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        loadRulesFromProperties();
        addDefaultRules();
        compileRules();
        sortRules();
        log.info("Log router initialized with {} rules", rules.size());
    }

    /**
     * Determines the destinations for a log event.
     * 
     * @param event the log event
     * @return set of destinations that should receive this event
     */
    public Set<LogDestination> route(ILoggingEvent event) {
        Set<LogDestination> destinations = new LinkedHashSet<>();

        for (RoutingRule rule : rules) {
            if (!rule.isEnabled()) {
                continue;
            }

            if (matches(rule, event)) {
                destinations.addAll(rule.getDestinations());

                if (rule.isTerminal()) {
                    break;
                }
            }
        }

        // Default to console if no destinations matched
        if (destinations.isEmpty()) {
            destinations.add(LogDestination.CONSOLE);
        }

        return destinations;
    }

    /**
     * Checks if a rule matches a log event.
     */
    private boolean matches(RoutingRule rule, ILoggingEvent event) {
        // Level check
        if (!rule.matchesLevel(event.getLevel())) {
            return false;
        }

        // Logger name check
        if (!rule.matchesLogger(event.getLoggerName())) {
            return false;
        }

        // Marker check
        if (rule.getMarkers() != null && !rule.getMarkers().isEmpty()) {
            if (!matchesMarker(rule.getMarkers(), event.getMarkerList())) {
                return false;
            }
        }

        // MDC check
        if (rule.getRequiredMdcKeys() != null && !rule.getRequiredMdcKeys().isEmpty()) {
            Map<String, String> mdc = event.getMDCPropertyMap();
            for (String key : rule.getRequiredMdcKeys()) {
                if (!mdc.containsKey(key)) {
                    return false;
                }
            }
        }

        // MDC value match
        if (rule.getMdcMatches() != null && !rule.getMdcMatches().isEmpty()) {
            Map<String, String> mdc = event.getMDCPropertyMap();
            for (Map.Entry<String, String> entry : rule.getMdcMatches().entrySet()) {
                if (!entry.getValue().equals(mdc.get(entry.getKey()))) {
                    return false;
                }
            }
        }

        // Message pattern check
        if (!rule.matchesMessage(event.getFormattedMessage())) {
            return false;
        }

        // Exception type check
        if (rule.getExceptionTypes() != null && !rule.getExceptionTypes().isEmpty()) {
            IThrowableProxy throwable = event.getThrowableProxy();
            if (throwable == null) {
                return false;
            }
            if (!rule.getExceptionTypes().contains(throwable.getClassName())) {
                return false;
            }
        }

        return true;
    }

    private boolean matchesMarker(Set<String> ruleMarkers, List<Marker> eventMarkers) {
        if (eventMarkers == null || eventMarkers.isEmpty()) {
            return false;
        }
        for (Marker marker : eventMarkers) {
            if (ruleMarkers.contains(marker.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds a routing rule dynamically.
     */
    public void addRule(RoutingRule rule) {
        rule.compile();
        rules.add(rule);
        rulesByName.put(rule.getName(), rule);
        sortRules();
    }

    /**
     * Removes a routing rule by name.
     */
    public void removeRule(String name) {
        RoutingRule rule = rulesByName.remove(name);
        if (rule != null) {
            rules.remove(rule);
        }
    }

    /**
     * Gets a rule by name.
     */
    public Optional<RoutingRule> getRule(String name) {
        return Optional.ofNullable(rulesByName.get(name));
    }

    /**
     * Gets all configured rules.
     */
    public List<RoutingRule> getRules() {
        return new ArrayList<>(rules);
    }

    /**
     * Enables or disables a rule.
     */
    public void setRuleEnabled(String name, boolean enabled) {
        RoutingRule rule = rulesByName.get(name);
        if (rule != null) {
            rule.setEnabled(enabled);
        }
    }

    // ==================== Private Methods ====================

    private void loadRulesFromProperties() {
        // Load rules from configuration
        Map<String, LoggingProperties.RoutingRuleConfig> configRules = properties.getRouting().getRules();
        if (configRules != null) {
            for (Map.Entry<String, LoggingProperties.RoutingRuleConfig> entry : configRules.entrySet()) {
                RoutingRule rule = convertConfigToRule(entry.getKey(), entry.getValue());
                rules.add(rule);
                rulesByName.put(rule.getName(), rule);
            }
        }
    }

    private RoutingRule convertConfigToRule(String name, LoggingProperties.RoutingRuleConfig config) {
        RoutingRule.RoutingRuleBuilder builder = RoutingRule.builder()
            .name(name)
            .description(config.getDescription())
            .enabled(config.isEnabled())
            .priority(config.getPriority())
            .terminal(config.isTerminal());

        if (config.getMinLevel() != null) {
            builder.minLevel(Level.toLevel(config.getMinLevel()));
        }
        if (config.getMaxLevel() != null) {
            builder.maxLevel(Level.toLevel(config.getMaxLevel()));
        }
        if (config.getLevels() != null) {
            Set<Level> levels = new HashSet<>();
            for (String level : config.getLevels()) {
                levels.add(Level.toLevel(level));
            }
            builder.levels(levels);
        }

        builder.loggerPatterns(config.getLoggerPatterns());
        builder.markers(config.getMarkers() != null ? new HashSet<>(config.getMarkers()) : null);
        builder.messagePatterns(config.getMessagePatterns());
        builder.exceptionTypes(config.getExceptionTypes() != null ? new HashSet<>(config.getExceptionTypes()) : null);
        builder.destinations(config.getDestinations());
        builder.addTags(config.getAddTags());

        return builder.build();
    }

    private void addDefaultRules() {
        // Default rule: errors to all destinations
        if (!rulesByName.containsKey("errors-everywhere")) {
            rules.add(RoutingRule.builder()
                .name("errors-everywhere")
                .description("Route all errors to all configured destinations")
                .priority(1)
                .minLevel(Level.ERROR)
                .destinations(List.of(LogDestination.CONSOLE, LogDestination.FILE))
                .terminal(false)
                .build());
        }

        // Default rule: security logs to dedicated destination
        if (!rulesByName.containsKey("security-logs")) {
            rules.add(RoutingRule.builder()
                .name("security-logs")
                .description("Route security-related logs")
                .priority(10)
                .loggerPatterns(List.of("*.security.*", "*.auth.*", "*.audit.*"))
                .destinations(List.of(LogDestination.FILE))
                .terminal(false)
                .build());
        }

        // Default rule: HTTP traffic logs
        if (!rulesByName.containsKey("http-traffic")) {
            rules.add(RoutingRule.builder()
                .name("http-traffic")
                .description("Route HTTP traffic logs")
                .priority(20)
                .loggerPatterns(List.of("org.zalando.logbook.*"))
                .destinations(List.of(LogDestination.FILE))
                .terminal(false)
                .build());
        }
    }

    private void compileRules() {
        for (RoutingRule rule : rules) {
            rule.compile();
        }
    }

    private void sortRules() {
        rules.sort(Comparator.comparingInt(RoutingRule::getPriority));
    }
}
