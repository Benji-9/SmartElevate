package com.smartelevate.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

/** Los límites de login se validan al arrancar (#225): fuera de rango, la app no levanta. */
class LoginThrottlePropertiesTest {

    @EnableConfigurationProperties(LoginThrottleProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Config.class);

    @Test
    void defaultsMatchTheRules() {
        runner.run(ctx -> assertThat(ctx.getBean(LoginThrottleProperties.class))
                .isEqualTo(new LoginThrottleProperties(Duration.ofMinutes(15), 10, 100)));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    void theAccountLimitAcceptsTheNistRange(int limit) {
        runner.withPropertyValues("app.login-throttle.max-failures-per-account=" + limit)
                .run(ctx -> assertThat(ctx.getBean(LoginThrottleProperties.class).maxFailuresPerAccount())
                        .isEqualTo(limit));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "app.login-throttle.max-failures-per-account=0",
            "app.login-throttle.max-failures-per-account=101",
            "app.login-throttle.max-failures-per-ip=0",
            "app.login-throttle.window=PT0S",
    })
    void outOfRangeValuesPreventStartup(String property) {
        runner.withPropertyValues(property).run(ctx -> assertThat(ctx).hasFailed());
    }
}
