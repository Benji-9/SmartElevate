package com.smartelevate.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartelevate.common.config.CorsProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RefreshCookieTest {

    @org.springframework.context.annotation.Configuration
    @org.springframework.boot.context.properties.EnableConfigurationProperties(RefreshCookieProperties.class)
    static class PropsConfig {
    }

    @Test
    void withoutTheDevProfileTheRealDefaultIsSecureLaxAndScopedToAuth() {
        // Lee el application.yml de verdad, sin el application-dev.yml que apaga Secure.
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withInitializer(new org.springframework.boot.test.context.ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=prod")
                .withUserConfiguration(PropsConfig.class)
                .run(ctx -> assertThat(ctx.getBean(RefreshCookieProperties.class))
                        .isEqualTo(new RefreshCookieProperties("refresh_token", true, "Lax", "/api/auth")));
    }

    private final RefreshCookie cookie = new RefreshCookie(
            new RefreshCookieProperties(null, true, null, null),
            new JwtProperties("x", Duration.ofMinutes(15), Duration.ofDays(7)),
            new CorsProperties(List.of("https://app.example")));

    @Test
    void issuedCookieIsHttpOnlySecureLaxAndScopedToAuth() {
        assertThat(cookie.issue("abc")).startsWith("refresh_token=abc;")
                .contains("Max-Age=604800", "Path=/api/auth", "Secure", "HttpOnly", "SameSite=Lax");
    }

    @Test
    void expiredCookieHasZeroMaxAge() {
        assertThat(cookie.expire()).startsWith("refresh_token=;").contains("Max-Age=0", "HttpOnly", "Secure");
    }

    @Test
    void csrfCheckNeedsTheHeaderAndAnAllowedOriginWhenPresent() {
        MockHttpServletRequest ok = new MockHttpServletRequest();
        ok.addHeader("X-Requested-With", "SmartElevate");
        assertThatCode(() -> cookie.requireSameSiteRequest(ok)).doesNotThrowAnyException();
        ok.addHeader("Origin", "https://app.example");
        assertThatCode(() -> cookie.requireSameSiteRequest(ok)).doesNotThrowAnyException();

        assertThatThrownBy(() -> cookie.requireSameSiteRequest(new MockHttpServletRequest()))
                .isInstanceOf(CsrfRejectedException.class);
        MockHttpServletRequest bad = new MockHttpServletRequest();
        bad.addHeader("X-Requested-With", "SmartElevate");
        bad.addHeader("Origin", "https://evil.example");
        assertThatThrownBy(() -> cookie.requireSameSiteRequest(bad)).isInstanceOf(CsrfRejectedException.class);
    }
}
