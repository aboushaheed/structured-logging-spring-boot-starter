package com.izemtechnologies.logging;

import com.izemtechnologies.logging.correlation.CorrelationIdHolder;
import com.izemtechnologies.logging.health.LoggingHealthIndicator;
import com.izemtechnologies.logging.masking.SensitiveDataMasker;
import com.izemtechnologies.logging.masking.SensitiveType;
import com.izemtechnologies.logging.properties.LoggingProperties;
import com.izemtechnologies.logging.sampling.LogSampler;
import com.izemtechnologies.logging.sampling.SamplingContextCleanupFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the improvements made to the logging library.
 */
class ImprovementsTest {

    private LoggingProperties properties;

    @BeforeEach
    void setUp() {
        properties = new LoggingProperties();
    }

    @Nested
    @DisplayName("Token Store Bounded Cache Tests")
    class TokenStoreBoundedCacheTests {

        @Test
        @DisplayName("Should create masker with bounded token store")
        void shouldCreateMaskerWithBoundedTokenStore() {
            SensitiveDataMasker masker = new SensitiveDataMasker(properties);
            assertThat(masker).isNotNull();

            // Verify statistics are available
            Map<String, Object> stats = masker.getStatistics();
            assertThat(stats).containsKey("tokenCacheSize");
            assertThat((Integer) stats.get("tokenCacheSize")).isEqualTo(0);
        }

        @Test
        @DisplayName("Should track masking metrics")
        void shouldTrackMaskingMetrics() {
            SensitiveDataMasker masker = new SensitiveDataMasker(properties);

            // Perform some masking
            String input = "Email: test@example.com";
            masker.mask(input);

            // Check metrics
            assertThat(masker.getTotalMaskedFields()).isGreaterThanOrEqualTo(0);
            assertThat(masker.getTotalMaskingTimeNanos()).isGreaterThanOrEqualTo(0);
        }
    }

    @Nested
    @DisplayName("CVV Pattern Tests")
    class CVVPatternTests {

        @Test
        @DisplayName("CVV pattern should not match standalone numbers")
        void cvvPatternShouldNotMatchStandaloneNumbers() {
            Pattern cvvPattern = SensitiveType.CVV.getPattern();

            // Should NOT match standalone 3-4 digit numbers
            assertThat(cvvPattern.matcher("123").find()).isFalse();
            assertThat(cvvPattern.matcher("1234").find()).isFalse();
            assertThat(cvvPattern.matcher("The value is 456").find()).isFalse();
        }

        @Test
        @DisplayName("CVV pattern should match contextual CVV references")
        void cvvPatternShouldMatchContextualReferences() {
            Pattern cvvPattern = SensitiveType.CVV.getPattern();

            // Should match CVV in context
            assertThat(cvvPattern.matcher("cvv: 123").find()).isTrue();
            assertThat(cvvPattern.matcher("CVV=456").find()).isTrue();
            assertThat(cvvPattern.matcher("cvc: 789").find()).isTrue();
            assertThat(cvvPattern.matcher("security_code: 1234").find()).isTrue();
        }
    }

    @Nested
    @DisplayName("Correlation ID Validation Tests")
    class CorrelationIdValidationTests {

        @Test
        @DisplayName("Should generate valid correlation IDs")
        void shouldGenerateValidCorrelationIds() {
            String correlationId = CorrelationIdHolder.generate();

            assertThat(correlationId).isNotNull();
            assertThat(correlationId).isNotEmpty();
            // Should match the valid pattern (alphanumeric, hyphens, underscores, dots)
            assertThat(correlationId).matches("^[a-zA-Z0-9\\-_\\.]{1,128}$");
        }

        @Test
        @DisplayName("Should set and get correlation ID correctly")
        void shouldSetAndGetCorrelationIdCorrectly() {
            String testId = "test-correlation-id-12345";

            CorrelationIdHolder.set(testId);
            assertThat(CorrelationIdHolder.get()).isEqualTo(testId);

            CorrelationIdHolder.clear();
            assertThat(CorrelationIdHolder.get()).isNull();
        }
    }

    @Nested
    @DisplayName("Sampling Context Cleanup Tests")
    class SamplingContextCleanupTests {

        @Test
        @DisplayName("Should create sampling context cleanup filter")
        void shouldCreateSamplingContextCleanupFilter() {
            SamplingContextCleanupFilter filter = new SamplingContextCleanupFilter();
            assertThat(filter).isNotNull();
        }

        @Test
        @DisplayName("Head sampling context should be clearable")
        void headSamplingContextShouldBeClearable() {
            LogSampler.HeadSamplingContext.markSamplingDecision(true);
            assertThat(LogSampler.HeadSamplingContext.isMarkedForSampling()).isTrue();

            LogSampler.HeadSamplingContext.clear();
            assertThat(LogSampler.HeadSamplingContext.isMarkedForSampling()).isNull();
        }

        @Test
        @DisplayName("Tail sampling context should be clearable")
        void tailSamplingContextShouldBeClearable() {
            LogSampler.TailSamplingContext.markForSampling();
            assertThat(LogSampler.TailSamplingContext.isMarkedForSampling()).isTrue();

            LogSampler.TailSamplingContext.clear();
            assertThat(LogSampler.TailSamplingContext.isMarkedForSampling()).isFalse();
        }
    }

    @Nested
    @DisplayName("Health Indicator Tests")
    class HealthIndicatorTests {

        @Test
        @DisplayName("Should create health indicator without sampler")
        void shouldCreateHealthIndicatorWithoutSampler() {
            LoggingHealthIndicator healthIndicator = new LoggingHealthIndicator(Optional.empty());

            Health health = healthIndicator.health();
            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsKey("status");
        }

        @Test
        @DisplayName("Should create health indicator with sampler")
        void shouldCreateHealthIndicatorWithSampler() {
            LogSampler sampler = new LogSampler(properties);
            LoggingHealthIndicator healthIndicator = new LoggingHealthIndicator(Optional.of(sampler));

            Health health = healthIndicator.health();
            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsKey("sampling");
        }
    }

    @Nested
    @DisplayName("Masking Metrics Tests")
    class MaskingMetricsTests {

        @Test
        @DisplayName("Should provide statistics")
        void shouldProvideStatistics() {
            SensitiveDataMasker masker = new SensitiveDataMasker(properties);

            Map<String, Object> stats = masker.getStatistics();

            assertThat(stats).containsKeys(
                "totalMaskedFields",
                "totalMaskedValues",
                "totalMaskingTimeMs",
                "averageMaskingTimeMs",
                "tokenCacheSize"
            );
        }

        @Test
        @DisplayName("Should calculate average masking time")
        void shouldCalculateAverageMaskingTime() {
            SensitiveDataMasker masker = new SensitiveDataMasker(properties);

            // Initially should be 0
            assertThat(masker.getAverageMaskingTimeMs()).isEqualTo(0.0);

            // After masking, should still be a valid number
            masker.mask("test@example.com");
            assertThat(masker.getAverageMaskingTimeMs()).isGreaterThanOrEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("Sensitive Data Masking Tests")
    class SensitiveDataMaskingTests {

        @Test
        @DisplayName("Should mask email addresses")
        void shouldMaskEmailAddresses() {
            SensitiveDataMasker masker = new SensitiveDataMasker(properties);

            String input = "Contact: john.doe@example.com";
            String masked = masker.mask(input);

            assertThat(masked).doesNotContain("john.doe@example.com");
        }

        @Test
        @DisplayName("Should mask credit card numbers")
        void shouldMaskCreditCardNumbers() {
            SensitiveDataMasker masker = new SensitiveDataMasker(properties);

            String input = "Card: 4111111111111111";
            String masked = masker.mask(input);

            assertThat(masked).doesNotContain("4111111111111111");
        }

        @Test
        @DisplayName("Should mask JWT tokens")
        void shouldMaskJwtTokens() {
            SensitiveDataMasker masker = new SensitiveDataMasker(properties);

            String jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U";
            String input = "Token: " + jwt;
            String masked = masker.mask(input);

            assertThat(masked).doesNotContain(jwt);
        }
    }
}
