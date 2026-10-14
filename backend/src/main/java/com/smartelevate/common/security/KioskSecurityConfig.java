package com.smartelevate.common.security;

import static org.springframework.security.config.Customizer.withDefaults;

import com.smartelevate.elevator.service.KioskService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Cadena de seguridad propia de {@code /api/kiosks/**} (ADR 0020): los kioscos se autentican con
 * {@code Authorization: Bearer <secreto del kiosco>}, que el resource server JWT de {@link SecurityConfig} rechazaría
 * como un JWT inválido. Va antes ({@code @Order(1)}); el resto de {@code /api} no cambia.
 */
@Configuration
public class KioskSecurityConfig {

    static final String KIOSK_PATHS = "/api/kiosks/**";
    private static final String BEARER = "Bearer ";

    @Bean
    @Order(1)
    SecurityFilterChain kioskSecurityFilterChain(HttpSecurity http, KioskService kiosks,
                                                 AuthenticationEntryPoint entryPoint) {
        http
                .securityMatcher(KIOSK_PATHS)
                .cors(withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().hasRole("KIOSK"))
                // No es un @Component a propósito: Boot lo registraría también como filtro global del servlet.
                .addFilterBefore(new KioskCredentialFilter(kiosks, entryPoint), AuthorizationFilter.class)
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint));
        return http.build();
    }

    /** Sin credencial válida corta con 401 ({@link KioskAuthenticationException}); con credencial, autentica. */
    private static final class KioskCredentialFilter extends OncePerRequestFilter {

        private final KioskService kiosks;
        private final AuthenticationEntryPoint entryPoint;

        KioskCredentialFilter(KioskService kiosks, AuthenticationEntryPoint entryPoint) {
            this.kiosks = kiosks;
            this.entryPoint = entryPoint;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            Optional<CurrentKiosk> kiosk = header != null && header.startsWith(BEARER)
                    && !header.substring(BEARER.length()).isBlank()
                    ? kiosks.authenticate(header.substring(BEARER.length()).trim())
                    : Optional.empty();
            if (kiosk.isEmpty()) {
                entryPoint.commence(request, response, new KioskAuthenticationException());
                return;
            }
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(kiosk.get(), null,
                    List.of(new SimpleGrantedAuthority("ROLE_KIOSK"))));
            SecurityContextHolder.setContext(context);
            chain.doFilter(request, response);
        }
    }
}
