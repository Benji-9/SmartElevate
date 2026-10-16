package com.smartelevate.elevator.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

/**
 * Parámetros del kiosco al arrancar (#229). Que en prod falte la clave HMAC lo prueba RequiredProdPropertiesTest;
 * la clave corta la rechaza QrTokenService en todos los perfiles (QrTokenServiceTest).
 */
class KioskPropertiesTest {

    @EnableConfigurationProperties(KioskProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Config.class);

    @Test
    void defaults() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            KioskProperties props = ctx.getBean(KioskProperties.class);
            assertThat(props.qrTtl()).isEqualTo(Duration.ofSeconds(30));
            assertThat(props.simulatorEnabled()).isFalse();
            assertThat(props.rateLimitMaxRequests()).isEqualTo(10);
            assertThat(props.rateLimitWindow()).isEqualTo(Duration.ofMinutes(1));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "app.kiosk.qr-ttl=PT0S",
            "app.kiosk.qr-ttl=PT0.5S",
            "app.kiosk.rate-limit-max-requests=0",
            "app.kiosk.rate-limit-window=PT0S",
    })
    void outOfRangeValuesPreventStartup(String property) {
        runner.withPropertyValues(property).run(ctx -> assertThat(ctx).hasFailed());
    }
}
