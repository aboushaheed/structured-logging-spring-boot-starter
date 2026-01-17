package com.izemtechnologies.logging.config;

import com.izemtechnologies.logging.async.BackpressureAsyncAppender;
import com.izemtechnologies.logging.correlation.*;
import com.izemtechnologies.logging.enrichment.DefaultEnrichers;
import com.izemtechnologies.logging.enrichment.LogEnricher;
import com.izemtechnologies.logging.filter.RequestLoggingFilter;
import com.izemtechnologies.logging.level.DynamicLogLevelManager;
import com.izemtechnologies.logging.level.LogLevelEndpoint;
import com.izemtechnologies.logging.manager.LogMetadataManager;
import com.izemtechnologies.logging.manager.RuntimeLogTagsBean;
import com.izemtechnologies.logging.masking.SensitiveDataMasker;
import com.izemtechnologies.logging.metrics.LoggingMetrics;
import com.izemtechnologies.logging.metrics.LoggingMetricsAppender;
import com.izemtechnologies.logging.properties.LoggingProperties;
import com.izemtechnologies.logging.provider.*;
import com.izemtechnologies.logging.routing.LogRouter;
import com.izemtechnologies.logging.routing.RoutingAppender;
import com.izemtechnologies.logging.sampling.LogSampler;
import com.izemtechnologies.logging.sampling.SamplingTurboFilter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.List;
import java.util.Optional;

/**
 * Main auto-configuration class for the structured JSON logging starter.
 * 
 * <p>This configuration sets up all logging components including:</p>
 * <ul>
 *   <li>Programmatic Logback configuration with JSON output</li>
 *   <li>Dynamic and static tag management</li>
 *   <li>Sensitive data masking (RGPD/GDPR compliance)</li>
 *   <li>Correlation ID propagation</li>
 *   <li>Log sampling and async buffering</li>
 *   <li>Structured exception logging</li>
 *   <li>Log enrichment and routing</li>
 *   <li>Metrics and dynamic log level management</li>
 * </ul>
 * 
 * @author Souidi
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(name = "ch.qos.logback.classic.LoggerContext")
@ConditionalOnProperty(prefix = "app.logging", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(LoggingProperties.class)
@Import({LogbookAutoConfiguration.class, TracingAutoConfiguration.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LoggingAutoConfiguration {

    // ==================== Core Beans ====================

    @Bean
    @ConditionalOnMissingBean
    public RuntimeLogTagsBean runtimeLogTagsBean() {
        log.debug("Creating RuntimeLogTagsBean");
        return new RuntimeLogTagsBean();
    }

    @Bean
    @ConditionalOnMissingBean
    public LogMetadataManager logMetadataManager(RuntimeLogTagsBean runtimeLogTagsBean) {
        log.debug("Creating LogMetadataManager");
        return new LogMetadataManager(runtimeLogTagsBean);
    }

    @Bean
    @ConditionalOnMissingBean
    public LogbackConfigurator logbackConfigurator(LoggingProperties properties) {
        log.debug("Creating LogbackConfigurator");
        return new LogbackConfigurator(properties);
    }

    // ==================== Masking ====================

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "app.logging.masking", name = "enabled", havingValue = "true", matchIfMissing = true)
    public SensitiveDataMasker sensitiveDataMasker(LoggingProperties properties) {
        log.debug("Creating SensitiveDataMasker");
        return new SensitiveDataMasker(properties);
    }

    // ==================== Correlation ID ====================

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "app.logging.correlation", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CorrelationIdFilter correlationIdFilter(LoggingProperties properties) {
        log.debug("Creating CorrelationIdFilter");
        return new CorrelationIdFilter(properties);
    }

    @Bean
    @ConditionalOnBean(CorrelationIdFilter.class)
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration(CorrelationIdFilter filter) {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    public CorrelationIdRestTemplateInterceptor correlationIdRestTemplateInterceptor() {
        return new CorrelationIdRestTemplateInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.web.reactive.function.client.WebClient")
    public CorrelationIdWebClientFilter correlationIdWebClientFilter() {
        return new CorrelationIdWebClientFilter();
    }

    @Bean
    @ConditionalOnMissingBean
    public CorrelationIdTaskDecorator correlationIdTaskDecorator() {
        return new CorrelationIdTaskDecorator();
    }

    // ==================== Request Logging Filter ====================

    @Bean
    @ConditionalOnMissingBean
    public RequestLoggingFilter requestLoggingFilter(LoggingProperties properties) {
        log.debug("Creating RequestLoggingFilter");
        return new RequestLoggingFilter(properties);
    }

    @Bean
    public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilterRegistration(RequestLoggingFilter filter) {
        FilterRegistrationBean<RequestLoggingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    // ==================== Sampling ====================

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "app.logging.sampling", name = "enabled", havingValue = "true")
    public LogSampler logSampler(LoggingProperties properties) {
        log.debug("Creating LogSampler");
        return new LogSampler(properties);
    }

    // ==================== Routing ====================

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "app.logging.routing", name = "enabled", havingValue = "true")
    public LogRouter logRouter(LoggingProperties properties) {
        log.debug("Creating LogRouter");
        return new LogRouter(properties);
    }

    // ==================== Metrics ====================

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnProperty(prefix = "app.logging.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    public LoggingMetrics loggingMetrics(MeterRegistry meterRegistry) {
        log.debug("Creating LoggingMetrics");
        return new LoggingMetrics(meterRegistry);
    }

    // ==================== Dynamic Log Level ====================

    @Bean
    @ConditionalOnMissingBean(name = "loggingTaskScheduler")
    public TaskScheduler loggingTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("logging-scheduler-");
        scheduler.initialize();
        return scheduler;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "app.logging.dynamic-level", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DynamicLogLevelManager dynamicLogLevelManager(TaskScheduler loggingTaskScheduler) {
        log.debug("Creating DynamicLogLevelManager");
        return new DynamicLogLevelManager(loggingTaskScheduler);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(DynamicLogLevelManager.class)
    @ConditionalOnProperty(prefix = "app.logging.dynamic-level", name = "expose-endpoint", havingValue = "true", matchIfMissing = true)
    public LogLevelEndpoint logLevelEndpoint(DynamicLogLevelManager manager) {
        log.debug("Creating LogLevelEndpoint");
        return new LogLevelEndpoint(manager);
    }

    // ==================== Enrichers Configuration ====================

    @Configuration
    @ConditionalOnProperty(prefix = "app.logging.enrichment", name = "enabled", havingValue = "true", matchIfMissing = true)
    public static class EnrichmentConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "app.logging.enrichment", name = "include-application", havingValue = "true", matchIfMissing = true)
        public DefaultEnrichers.ApplicationEnricher applicationEnricher(
                Optional<BuildProperties> buildProperties, Environment environment) {
            return new DefaultEnrichers.ApplicationEnricher(buildProperties, environment);
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "app.logging.enrichment", name = "include-host", havingValue = "true", matchIfMissing = true)
        public DefaultEnrichers.HostEnricher hostEnricher() {
            return new DefaultEnrichers.HostEnricher();
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "app.logging.enrichment", name = "include-runtime", havingValue = "true", matchIfMissing = true)
        public DefaultEnrichers.RuntimeEnricher runtimeEnricher() {
            return new DefaultEnrichers.RuntimeEnricher();
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "app.logging.enrichment", name = "include-git", havingValue = "true", matchIfMissing = true)
        public DefaultEnrichers.GitEnricher gitEnricher(Optional<GitProperties> gitProperties) {
            return new DefaultEnrichers.GitEnricher(gitProperties);
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "app.logging.enrichment", name = "include-environment", havingValue = "true", matchIfMissing = true)
        public DefaultEnrichers.EnvironmentEnricher environmentEnricher() {
            return new DefaultEnrichers.EnvironmentEnricher();
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "app.logging.enrichment", name = "include-thread", havingValue = "true")
        public DefaultEnrichers.ThreadEnricher threadEnricher() {
            return new DefaultEnrichers.ThreadEnricher();
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "app.logging.enrichment", name = "include-memory", havingValue = "true")
        public DefaultEnrichers.MemoryEnricher memoryEnricher() {
            return new DefaultEnrichers.MemoryEnricher();
        }
    }

    // ==================== Context Initialization ====================

    @Bean
    public ApplicationListener<ContextRefreshedEvent> loggingInitializationListener(
            RuntimeLogTagsBean runtimeLogTagsBean,
            LogbackConfigurator logbackConfigurator,
            LoggingProperties properties,
            ApplicationContext applicationContext) {

        return event -> {
            log.info("Initializing structured JSON logging...");

            // Set static tags from configuration
            if (properties.getStaticTags() != null && !properties.getStaticTags().isEmpty()) {
                StaticTagsJsonProvider.setStaticTags(properties.getStaticTags());
                log.debug("Configured {} static tags", properties.getStaticTags().size());
            }

            // Wire the RuntimeLogTagsBean to the JsonProvider
            DynamicTagsJsonProvider.setRuntimeTagsBean(runtimeLogTagsBean);

            // Mark the bean as initialized
            runtimeLogTagsBean.markInitialized();

            // Initialize masking provider
            try {
                SensitiveDataMasker masker = applicationContext.getBean(SensitiveDataMasker.class);
                MaskingJsonProvider.setMasker(masker);
                log.debug("Sensitive data masking enabled");
            } catch (Exception e) {
                log.debug("SensitiveDataMasker not available, masking disabled");
            }

            // Initialize sampling
            try {
                LogSampler sampler = applicationContext.getBean(LogSampler.class);
                SamplingTurboFilter.setSampler(sampler);
                log.debug("Log sampling enabled");
            } catch (Exception e) {
                log.debug("LogSampler not available, sampling disabled");
            }

            // Initialize routing
            try {
                LogRouter router = applicationContext.getBean(LogRouter.class);
                RoutingAppender.setRouter(router);
                log.debug("Log routing enabled");
            } catch (Exception e) {
                log.debug("LogRouter not available, routing disabled");
            }

            // Initialize metrics
            try {
                LoggingMetrics metrics = applicationContext.getBean(LoggingMetrics.class);
                LoggingMetricsAppender.setMetrics(metrics);
                log.debug("Logging metrics enabled");
            } catch (Exception e) {
                log.debug("LoggingMetrics not available, metrics disabled");
            }

            // Initialize enrichers
            try {
                List<LogEnricher> enrichers = applicationContext.getBeansOfType(LogEnricher.class)
                    .values().stream().toList();
                if (!enrichers.isEmpty()) {
                    LogEnrichmentJsonProvider.setEnrichers(enrichers);
                    log.info("Registered {} log enrichers", enrichers.size());
                }
            } catch (Exception e) {
                log.debug("No enrichers available");
            }

            // Configure Logback programmatically
            logbackConfigurator.configure();

            log.info("Structured JSON logging initialized successfully with all features");
        };
    }
}
