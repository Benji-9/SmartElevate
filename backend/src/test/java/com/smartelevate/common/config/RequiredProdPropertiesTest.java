package com.smartelevate.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartelevate.user.service.VerificationProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ContextConsumer;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * Variables obligatorias en prod (#222). Carga los application*.yml reales y solo los properties (sin base ni
 * web): en prod falta una y no arranca; en dev arranca sin ninguna.
 */
class RequiredProdPropertiesTest {

    private static final String SECRET = "app.security.jwt.secret=0123456789abcdef0123456789abcdef";
    private static final String FRONTEND_URL = "app.verification.frontend-url=https://smart-elevate.vercel.app";
    private static final String CORS = "app.cors.allowed-origins=https://smart-elevate.vercel.app";

    @Configuration
    @ConfigurationPropertiesScan(basePackageClasses = {RequiredProdProperties.class, VerificationProperties.class})
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Config.class);

    private ApplicationContextRunner prod(String... properties) {
        return runner.withPropertyValues("spring.profiles.active=prod").withPropertyValues(properties);
    }

    @Test
    void prodStartsWithAllRequiredVariables() {
        prod(SECRET, FRONTEND_URL, CORS).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(CorsProperties.class).allowedOrigins())
                    .containsExactly("https://smart-elevate.vercel.app");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void prodFailsWithoutJwtSecret(String value) {
        prod("app.security.jwt.secret=" + value, FRONTEND_URL, CORS).run(failsOn("security.jwt.secret"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void prodFailsWithoutFrontendUrl(String value) {
        prod(SECRET, "app.verification.frontend-url=" + value, CORS)
                .run(failsOn("verification.frontendUrl"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", " , "})
    void prodFailsWithoutCorsOrigins(String value) {
        prod(SECRET, FRONTEND_URL, "app.cors.allowed-origins=" + value)
                .run(failsOn("cors.allowedOrigins"));
    }

    /** Falla por la validación de ese campo (ruta del record, en camelCase), no por otra cosa. */
    private static ContextConsumer<AssertableApplicationContext> failsOn(String property) {
        return ctx -> assertThat(ctx).getFailure()
                .rootCause().isInstanceOf(BindValidationException.class).hasMessageContaining(property);
    }

    @Test
    void devStartsWithoutAnyAndUsesLocalhost() {
        runner.withPropertyValues("spring.profiles.active=dev").run(ctx -> {
            assertThat(ctx).hasNotFailed().doesNotHaveBean(RequiredProdProperties.class);
            assertThat(ctx.getBean(VerificationProperties.class).frontendUrl()).isEqualTo("http://localhost:5173");
            assertThat(ctx.getBean(CorsProperties.class).allowedOrigins()).isEqualTo(List.of("http://localhost:5173"));
        });
    }
}
