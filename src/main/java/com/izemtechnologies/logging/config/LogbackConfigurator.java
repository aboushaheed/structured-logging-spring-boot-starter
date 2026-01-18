package com.izemtechnologies.logging.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.util.FileSize;
import com.izemtechnologies.logging.properties.LoggingProperties;
import com.izemtechnologies.logging.provider.DynamicTagsJsonProvider;
import com.izemtechnologies.logging.provider.KeyValuePairsJsonProvider;
import com.izemtechnologies.logging.provider.StaticTagsJsonProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.composite.loggingevent.ArgumentsJsonProvider;
import net.logstash.logback.composite.loggingevent.CallerDataJsonProvider;
import net.logstash.logback.composite.loggingevent.LogLevelJsonProvider;
import net.logstash.logback.composite.loggingevent.LoggerNameJsonProvider;
import net.logstash.logback.composite.loggingevent.LoggingEventFormattedTimestampJsonProvider;
import net.logstash.logback.composite.loggingevent.LoggingEventThreadNameJsonProvider;
import net.logstash.logback.composite.loggingevent.MdcJsonProvider;
import net.logstash.logback.composite.loggingevent.MessageJsonProvider;
import net.logstash.logback.composite.loggingevent.StackTraceJsonProvider;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.TimeZone;

/**
 * Programmatic Logback configurator that sets up JSON logging without requiring logback.xml.
 * <p>
 * This configurator creates and configures:
 * <ul>
 *     <li>Console appender with JSON output</li>
 *     <li>Optional file appender with rolling policy</li>
 *     <li>Custom JSON providers for static tags, dynamic tags, and SLF4J 2.x key-value pairs</li>
 *     <li>Tracing integration for TraceID/SpanID</li>
 * </ul>
 * </p>
 *
 * @author Logging Starter Team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class LogbackConfigurator {

    private static final String CONSOLE_APPENDER_NAME = "JSON_CONSOLE";
    private static final String FILE_APPENDER_NAME = "JSON_FILE";

    private final LoggingProperties properties;

    /**
     * Configures Logback programmatically based on the provided properties.
     */
    public void configure() {
        try {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

            // Get the root logger
            Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);

            // Remove all existing appenders
            rootLogger.detachAndStopAllAppenders();

            // Configure console appender
            if (properties.getConsole().isEnabled()) {
                ConsoleAppender<ILoggingEvent> consoleAppender = createConsoleAppender(context);
                rootLogger.addAppender(consoleAppender);
            }

            // Configure file appender
            if (properties.getFile().isEnabled()) {
                RollingFileAppender<ILoggingEvent> fileAppender = createFileAppender(context);
                rootLogger.addAppender(fileAppender);
            }

            // Set root logger level from configuration (default to INFO)
            String configuredLevel = properties.getConsole().getLevel();
            Level rootLevel = Level.toLevel(configuredLevel, Level.INFO);
            rootLogger.setLevel(rootLevel);

            log.info("Logback configured programmatically with JSON output");
        } catch (Exception e) {
            // Print to stderr since logger may not be available
            log.error("ERROR: Failed to configure Logback programmatically");
            throw new RuntimeException("Failed to configure Logback", e);
        }
    }

    /**
     * Creates and configures the console appender.
     */
    private ConsoleAppender<ILoggingEvent> createConsoleAppender(LoggerContext context) {
        ConsoleAppender<ILoggingEvent> appender = new ConsoleAppender<>();
        appender.setContext(context);
        appender.setName(CONSOLE_APPENDER_NAME);
        
        if (properties.getJson().isEnabled()) {
            appender.setEncoder(createJsonEncoder(context));
        } else {
            appender.setEncoder(createPatternEncoder(context));
        }
        
        appender.start();
        return appender;
    }

    /**
     * Creates and configures the file appender with rolling policy.
     */
    private RollingFileAppender<ILoggingEvent> createFileAppender(LoggerContext context) {
        LoggingProperties.FileProperties fileProps = properties.getFile();
        
        RollingFileAppender<ILoggingEvent> appender = new RollingFileAppender<>();
        appender.setContext(context);
        appender.setName(FILE_APPENDER_NAME);
        appender.setFile(fileProps.getPath());
        
        // Configure rolling policy
        SizeAndTimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new SizeAndTimeBasedRollingPolicy<>();
        rollingPolicy.setContext(context);
        rollingPolicy.setParent(appender);
        rollingPolicy.setFileNamePattern(fileProps.getPath() + ".%d{yyyy-MM-dd}.%i.gz");
        rollingPolicy.setMaxFileSize(FileSize.valueOf(fileProps.getMaxSize()));
        rollingPolicy.setMaxHistory(fileProps.getMaxHistory());
        rollingPolicy.setTotalSizeCap(FileSize.valueOf(fileProps.getTotalSizeCap()));
        rollingPolicy.start();
        
        appender.setRollingPolicy(rollingPolicy);
        
        if (properties.getJson().isEnabled()) {
            appender.setEncoder(createJsonEncoder(context));
        } else {
            appender.setEncoder(createPatternEncoder(context));
        }
        
        appender.start();
        return appender;
    }

    /**
     * Spring Boot internal MDC keys to exclude from JSON output.
     */
    private static final java.util.List<String> EXCLUDED_MDC_KEYS = java.util.Arrays.asList(
        "LOG_CORRELATION_PATTERN",
        "CONSOLE_LOG_PATTERN",
        "CONSOLE_LOG_CHARSET",
        "CONSOLE_LOG_THRESHOLD",
        "CONSOLE_LOG_STRUCTURED_FORMAT",
        "FILE_LOG_PATTERN",
        "FILE_LOG_CHARSET",
        "FILE_LOG_THRESHOLD",
        "FILE_LOG_STRUCTURED_FORMAT",
        "APPLICATION_NAME",
        "LOGGED_APPLICATION_NAME",
        "APPLICATION_GROUP",
        "PID"
    );

    /**
     * Creates the JSON encoder with all custom providers.
     * Uses LoggingEventCompositeJsonEncoder for full control over providers.
     */
    private Encoder<ILoggingEvent> createJsonEncoder(LoggerContext context) {
        LoggingProperties.JsonProperties jsonProps = properties.getJson();

        // Use composite encoder for full control (no default providers)
        net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder encoder =
            new net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder();
        encoder.setContext(context);

        // Get providers collection
        var providers = encoder.getProviders();
        providers.setContext(context);

        // Timestamp provider
        LoggingEventFormattedTimestampJsonProvider timestampProvider = new LoggingEventFormattedTimestampJsonProvider();
        timestampProvider.setFieldName("@timestamp");
        timestampProvider.setPattern(jsonProps.getTimestampFormat());
        timestampProvider.setTimeZone(jsonProps.getTimezone());
        providers.addProvider(timestampProvider);

        // Log level provider
        LogLevelJsonProvider levelProvider = new LogLevelJsonProvider();
        levelProvider.setFieldName("level");
        providers.addProvider(levelProvider);

        // Logger name provider
        if (jsonProps.isIncludeContext()) {
            LoggerNameJsonProvider loggerNameProvider = new LoggerNameJsonProvider();
            loggerNameProvider.setFieldName("logger");
            providers.addProvider(loggerNameProvider);

            LoggingEventThreadNameJsonProvider threadNameProvider = new LoggingEventThreadNameJsonProvider();
            threadNameProvider.setFieldName("thread");
            providers.addProvider(threadNameProvider);
        }

        // Message provider
        MessageJsonProvider messageProvider = new MessageJsonProvider();
        messageProvider.setFieldName("message");
        providers.addProvider(messageProvider);

        // MDC provider with exclusions for Spring Boot internal keys
        if (jsonProps.isIncludeMdc()) {
            MdcJsonProvider mdcProvider = new MdcJsonProvider();
            mdcProvider.setExcludeMdcKeyNames(EXCLUDED_MDC_KEYS);
            providers.addProvider(mdcProvider);
        }

        // Caller data provider (performance impact)
        if (jsonProps.isIncludeCallerData()) {
            providers.addProvider(new CallerDataJsonProvider());
        }

        // Stack trace provider
        if (jsonProps.isIncludeStackTrace()) {
            providers.addProvider(new StackTraceJsonProvider());
        }

        // Static tags provider
        StaticTagsJsonProvider staticTagsProvider = new StaticTagsJsonProvider();
        staticTagsProvider.setContext(context);
        providers.addProvider(staticTagsProvider);

        // Dynamic tags provider
        DynamicTagsJsonProvider dynamicTagsProvider = new DynamicTagsJsonProvider();
        dynamicTagsProvider.setContext(context);
        dynamicTagsProvider.setTagPrefix(properties.getDynamicTags().getPrefix());
        dynamicTagsProvider.setNestTags(properties.getDynamicTags().isNested());
        dynamicTagsProvider.setNestedFieldName(properties.getDynamicTags().getNestedFieldName());
        providers.addProvider(dynamicTagsProvider);

        // SLF4J 2.x Key-Value pairs provider
        KeyValuePairsJsonProvider kvpProvider = new KeyValuePairsJsonProvider();
        kvpProvider.setContext(context);
        providers.addProvider(kvpProvider);

        // Arguments provider for structured arguments
        providers.addProvider(new ArgumentsJsonProvider());

        // Pretty print (not recommended for production)
        if (jsonProps.isPrettyPrint()) {
            encoder.setJsonGeneratorDecorator(
                    new net.logstash.logback.decorate.PrettyPrintingJsonGeneratorDecorator()
            );
        }

        encoder.start();
        return encoder;
    }

    /**
     * Creates a pattern encoder for non-JSON output.
     */
    private Encoder<ILoggingEvent> createPatternEncoder(LoggerContext context) {
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
        encoder.setCharset(StandardCharsets.UTF_8);
        encoder.start();
        return encoder;
    }

    /**
     * Reconfigures Logback with updated properties.
     * This can be called at runtime to apply configuration changes.
     */
    public void reconfigure() {
        log.info("Reconfiguring Logback...");
        configure();
    }
}
