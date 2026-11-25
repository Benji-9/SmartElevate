package com.smartelevate.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.smartelevate.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

/** Limpieza de cuentas que nunca verificaron el email (#344). */
class UnverifiedAccountCleanupTest {

    private static final Instant NOW = Instant.parse("2026-11-25T15:00:00Z");

    private final UserRepository users = mock(UserRepository.class);
    private final UnverifiedAccountCleanup cleanup = new UnverifiedAccountCleanup(users,
            new UnverifiedCleanupProperties(Duration.ofDays(7), Duration.ofHours(1)),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void deletesTheAccountsOlderThanTheTtlAndReportsHowManyOnes() {
        given(users.deleteStaleUnverified(NOW.minus(Duration.ofDays(7)))).willReturn(3);

        assertThat(cleanup.deleteStale()).isEqualTo(3);

        verify(users).deleteStaleUnverified(Instant.parse("2026-11-18T15:00:00Z"));
    }

    @Test
    void doesNothingWhenThereIsNothingToDelete() {
        assertThat(cleanup.deleteStale()).isZero();
    }

    // --- Propiedades ---

    @EnableConfigurationProperties(UnverifiedCleanupProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Config.class);

    @Test
    void defaultsAreSevenDaysAndOneHour() {
        runner.run(ctx -> assertThat(ctx.getBean(UnverifiedCleanupProperties.class))
                .isEqualTo(new UnverifiedCleanupProperties(Duration.ofDays(7), Duration.ofHours(1))));
    }

    @Test
    void theValuesCanBeOverriddenByProperty() {
        runner.withPropertyValues("app.unverified-cleanup.ttl=P1D", "app.unverified-cleanup.interval=PT10M")
                .run(ctx -> assertThat(ctx.getBean(UnverifiedCleanupProperties.class))
                        .isEqualTo(new UnverifiedCleanupProperties(Duration.ofDays(1), Duration.ofMinutes(10))));
    }

    @Test
    void aNonPositiveTtlOrIntervalStopsTheAppFromStarting() {
        runner.withPropertyValues("app.unverified-cleanup.ttl=PT0S")
                .run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("app.unverified-cleanup.interval=-PT1M")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void theRecordRejectsNonPositiveDurationsDirectly() {
        assertThatThrownBy(() -> new UnverifiedCleanupProperties(Duration.ZERO, Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UnverifiedCleanupProperties(Duration.ofDays(7), Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
