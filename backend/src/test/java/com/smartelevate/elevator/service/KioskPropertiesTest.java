package com.smartelevate.elevator.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

/** Parámetros del kiosco al arrancar: en prod, sin clave HMAC (o corta) la app no levanta (#229). */
class KioskPropertiesTest {

    private static final String KEY = "app.kiosk.qr-secret=una-clave-de-prueba-de-al-menos-32-bytes";

    // El mismo registro que la app (@ConfigurationPropertiesScan), así se prueba que @Profile("prod") aplica.
    @ConfigurationPropertiesScan(basePackageClasses = KioskProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Config.class);

    private ApplicationContextRunner prod() {
        return runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"));
    }

    @Test
    void defaults() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            KioskProperties props = ctx.getBean(KioskProperties.class);
            assertThat(props.qrTtl()).isEqualTo(Duration.ofSeconds(30));
            assertThat(props.simulatorEnabled()).isFalse();
            assertThat(props.rateLimitMaxRequests()).isEqualTo(10);
            assertThat(props.rateLimitWindow()).isEqualTo(Duration.ofMinutes(1));
            // Fuera de prod la clave no es obligatoria: se usa una efímera.
            assertThat(ctx).doesNotHaveBean(KioskProdProperties.class);
        });
    }

    @Test
    void prodWithoutTheHmacKeyDoesNotStart() {
        prod().run(ctx -> assertThat(ctx).hasFailed());
        prod().withPropertyValues("app.kiosk.qr-secret=").run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void prodWithAShortHmacKeyDoesNotStart() {
        prod().withPropertyValues("app.kiosk.qr-secret=corta").run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void prodWithTheHmacKeyStarts() {
        prod().withPropertyValues(KEY)
                .run(ctx -> assertThat(ctx).hasNotFailed().hasSingleBean(KioskProdProperties.class));
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
