package com.izemtechnologies.logging.properties;

import com.izemtechnologies.logging.async.BackpressureStrategy;
import com.izemtechnologies.logging.masking.MaskingMode;
import com.izemtechnologies.logging.masking.SensitiveType;
import com.izemtechnologies.logging.routing.LogDestination;
import com.izemtechnologies.logging.sampling.SamplingStrategy;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Configuration properties for the structured JSON logging starter.
 * 
 * <p>All properties are prefixed with {@code app.logging}.</p>
 * 
 * @author Souidi
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.logging")
public class LoggingProperties {

    /**
     * Whether the logging starter is enabled.
     */
    private boolean enabled = true;

    /**
     * Application name for log identification.
     */
    private String applicationName;

    /**
     * Static tags to include in every log line.
     */
    private Map<String, String> staticTags = new HashMap<>();

    /**
     * JSON output configuration.
     */
    private JsonProperties json = new JsonProperties();

    /**
     * Dynamic tags configuration.
     */
    private DynamicTagsProperties dynamicTags = new DynamicTagsProperties();

    /**
     * Tracing configuration for TraceID/SpanID.
     */
    private TracingProperties tracing = new TracingProperties();

    /**
     * Correlation ID configuration.
     */
    private CorrelationProperties correlation = new CorrelationProperties();

    /**
     * Sensitive data masking configuration (RGPD/GDPR compliance).
     */
    private MaskingProperties masking = new MaskingProperties();

    /**
     * Log sampling configuration.
     */
    private SamplingProperties sampling = new SamplingProperties();

    /**
     * Async buffer configuration.
     */
    private AsyncProperties async = new AsyncProperties();

    /**
     * Exception formatting configuration.
     */
    private ExceptionProperties exception = new ExceptionProperties();

    /**
     * Log enrichment configuration.
     */
    private EnrichmentProperties enrichment = new EnrichmentProperties();

    /**
     * Log routing configuration.
     */
    private RoutingProperties routing = new RoutingProperties();

    /**
     * Logbook HTTP traffic logging configuration.
     */
    private LogbookProperties logbook = new LogbookProperties();

    /**
     * Metrics configuration.
     */
    private MetricsProperties metrics = new MetricsProperties();

    /**
     * Dynamic log level configuration.
     */
    private DynamicLevelProperties dynamicLevel = new DynamicLevelProperties();

    /**
     * Console appender configuration.
     */
    private ConsoleProperties console = new ConsoleProperties();

    /**
     * File appender configuration.
     */
    private FileProperties file = new FileProperties();

    /**
     * Request context configuration.
     */
    private RequestContextProperties requestContext = new RequestContextProperties();

    // ==================== Nested Configuration Classes ====================

    @Data
    public static class JsonProperties {
        private boolean enabled = true;
        private boolean prettyPrint = false;
        private boolean includeContext = true;
        private boolean includeMdc = true;
        private boolean includeMarkers = true;
        private boolean includeCallerData = false;
        private boolean includeStackTrace = true;
        private String timestampFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
        private String timezone = "UTC";
        private String timestampFieldName = "@timestamp";
        private String messageFieldName = "message";
        private String levelFieldName = "level";
        private String loggerFieldName = "logger";
    }

    @Data
    public static class DynamicTagsProperties {
        private String prefix = "";
        private boolean nested = false;
        private String nestedFieldName = "dynamic_tags";
    }

    @Data
    public static class TracingProperties {
        private boolean enabled = true;
        private String traceIdField = "traceId";
        private String spanIdField = "spanId";
    }

    @Data
    public static class CorrelationProperties {
        private boolean enabled = true;
        private String headerName = "X-Correlation-ID";
        private String responseHeaderName = "X-Correlation-ID";
        private boolean includeRequestIdHeader = true;
        private boolean generateIfMissing = true;
        private List<String> excludePaths = new ArrayList<>(List.of(
            "/actuator/**",
            "/health/**",
            "/metrics/**"
        ));
    }

    @Data
    public static class MaskingProperties {
        private boolean enabled = true;
        private MaskingMode defaultMode = MaskingMode.PARTIAL;
        private boolean maskPersonalData = true;
        private boolean maskFinancialData = true;
        private boolean maskSecrets = true;
        private boolean maskHealthData = true;
        private List<SensitiveType> enabledTypes = new ArrayList<>(List.of(
            SensitiveType.EMAIL,
            SensitiveType.PHONE,
            SensitiveType.CREDIT_CARD,
            SensitiveType.SSN,
            SensitiveType.FRENCH_NIR,
            SensitiveType.PASSWORD,
            SensitiveType.JWT,
            SensitiveType.BEARER_TOKEN,
            SensitiveType.API_KEY,
            SensitiveType.AWS_ACCESS_KEY,
            SensitiveType.AWS_SECRET_KEY,
            SensitiveType.AZURE_STORAGE_KEY,
            SensitiveType.GCP_API_KEY,
            SensitiveType.VAULT_TOKEN,
            SensitiveType.GITHUB_TOKEN,
            SensitiveType.DATABASE_URL,
            SensitiveType.PRIVATE_KEY,
            SensitiveType.IBAN
        ));
        private Map<String, String> customPatterns = new HashMap<>();
        private Set<String> maskedFields = new HashSet<>(Set.of(
            "password", "secret", "token", "apiKey", "api_key",
            "authorization", "credential", "private_key", "mot_de_passe"
        ));
        private Set<String> sensitiveKeywords = new HashSet<>(Set.of(
            "password", "secret", "token", "key", "credential",
            "auth", "api_key", "apikey", "private", "ssn", "card",
            "email", "phone", "address", "iban", "vault"
        ));
    }

    @Data
    public static class SamplingProperties {
        private boolean enabled = false;
        private SamplingStrategy strategy = SamplingStrategy.RATE;
        private double rate = 1.0;
        private int countInterval = 10;
        private boolean alwaysSampleErrors = true;
        private int highLoadThreshold = 1000;
        private int criticalLoadThreshold = 5000;
        private Map<String, Double> pathRates = new HashMap<>();
        private Map<String, Double> loggerRates = new HashMap<>();
    }

    @Data
    public static class AsyncProperties {
        private boolean enabled = true;
        private int bufferSize = 8192;
        private BackpressureStrategy backpressureStrategy = BackpressureStrategy.DROP_LOW_PRIORITY;
        private int warningThreshold = 80;
        private int criticalThreshold = 95;
        private String overflowFilePath = "logs/overflow.log";
        private int sampleRateUnderPressure = 10;
        private boolean includeCallerData = false;
    }

    @Data
    public static class ExceptionProperties {
        private boolean structured = true;
        private int maxStackFrames = 30;
        private int maxCauseDepth = 5;
        private boolean useSimpleClassName = false;
        private boolean includeSuppressed = true;
        private boolean includeStackHash = true;
        private Set<String> applicationPackages = new HashSet<>();
    }

    @Data
    public static class EnrichmentProperties {
        private boolean enabled = true;
        private boolean includeApplication = true;
        private boolean includeHost = true;
        private boolean includeRuntime = true;
        private boolean includeGit = true;
        private boolean includeEnvironment = true;
        private boolean includeThread = false;
        private boolean includeMemory = false;
        private String envVars = "ENV,REGION,DATACENTER,CLUSTER";
        private boolean nested = false;
        private String nestedFieldName = "context";
    }

    @Data
    public static class RoutingProperties {
        private boolean enabled = false;
        private Map<String, RoutingRuleConfig> rules = new HashMap<>();
        private List<LogDestination> defaultDestinations = new ArrayList<>(List.of(
            LogDestination.CONSOLE
        ));
    }

    @Data
    public static class RoutingRuleConfig {
        private String description;
        private boolean enabled = true;
        private int priority = 100;
        private String minLevel;
        private String maxLevel;
        private List<String> levels;
        private List<String> loggerPatterns;
        private List<String> markers;
        private List<String> messagePatterns;
        private Set<String> exceptionTypes;
        private List<LogDestination> destinations;
        private boolean terminal = false;
        private Map<String, String> addTags;
    }

    @Data
    public static class LogbookProperties {
        private boolean enabled = true;
        private String loggerName = "http.traffic";
        private String level = "INFO";
        private Set<String> maskedHeaders = new HashSet<>(Set.of(
            "Authorization", "X-Api-Key", "Cookie", "Set-Cookie"
        ));
        private Set<String> maskedBodyPaths = new HashSet<>(Set.of(
            "$.password", "$.secret", "$.token", "$.apiKey", "$.creditCard"
        ));
        private int maxBodySize = 8192;
        private boolean logRequestBody = true;
        private boolean logResponseBody = true;
        private Set<String> excludePaths = new HashSet<>(Set.of(
            "/actuator/**", "/health/**", "/favicon.ico"
        ));
    }

    @Data
    public static class MetricsProperties {
        private boolean enabled = true;
        private String prefix = "logging";
        private boolean includeByLevel = true;
        private boolean includeByLogger = false;
        private boolean includeLatency = true;
        private boolean includeBufferMetrics = true;
    }

    @Data
    public static class DynamicLevelProperties {
        private boolean enabled = true;
        private boolean exposeEndpoint = true;
        private int defaultDurationMinutes = 15;
        private int maxDurationMinutes = 60;
    }

    @Data
    public static class ConsoleProperties {
        private boolean enabled = true;
        private String level = "DEBUG";
    }

    @Data
    public static class FileProperties {
        private boolean enabled = false;
        private String path = "logs/application.log";
        private String level = "INFO";
        private String maxSize = "100MB";
        private int maxHistory = 30;
        private String totalSizeCap = "3GB";
    }

    @Data
    public static class RequestContextProperties {
        /**
         * Whether to log request completion with status and duration.
         */
        private boolean logRequestCompletion = true;

        /**
         * Headers to capture and include in logs.
         */
        private List<String> captureHeaders = new ArrayList<>(List.of(
            "X-Tenant-ID",
            "X-Request-ID",
            "X-Forwarded-For"
        ));

        /**
         * Paths to exclude from request logging.
         */
        private List<String> excludePaths = new ArrayList<>(List.of(
            "/actuator/**",
            "/health/**",
            "/metrics/**",
            "/favicon.ico"
        ));

        /**
         * Whether to include query parameters in logs.
         */
        private boolean includeQueryString = true;

        /**
         * Whether to include client IP in logs.
         */
        private boolean includeClientIp = true;

        /**
         * Whether to include User-Agent in logs.
         */
        private boolean includeUserAgent = true;

        /**
         * Maximum length for User-Agent string.
         */
        private int maxUserAgentLength = 200;
    }
}
