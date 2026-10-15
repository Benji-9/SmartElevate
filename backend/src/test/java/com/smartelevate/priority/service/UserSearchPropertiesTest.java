package com.smartelevate.priority.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

/** Los parámetros del buscador se validan al arrancar: fuera de rango, la app no levanta. */
class UserSearchPropertiesTest {

    @EnableConfigurationProperties(UserSearchProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Config.class);

    @Test
    void defaults() {
        runner.run(ctx -> assertThat(ctx.getBean(UserSearchProperties.class))
                .isEqualTo(new UserSearchProperties(3, 20)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "app.admin.user-search.min-query-length=1",
            "app.admin.user-search.page-size=0",
            "app.admin.user-search.page-size=101",
    })
    void outOfRangeValuesPreventStartup(String property) {
        runner.withPropertyValues(property).run(ctx -> assertThat(ctx).hasFailed());
    }
}
