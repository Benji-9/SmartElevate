package com.smartelevate.common.security;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Reglas de acceso de la API (#120). API stateless con access token Bearer: sin sesión ni CSRF.
 *
 * <h2>Públicos</h2>
 * {@code /api/ping}, {@code /api/auth/**} (registro, login, refresh, logout), {@code GET /api/elevators},
 * {@code GET /api/buildings}, la documentación ({@code /v3/api-docs/**}, {@code /swagger-ui*}),
 * {@code /actuator/health} y {@code /h2-console/**} (solo existe en dev). Todo lo demás exige un access token válido.
 *
 * <h2>Cómo agregar un endpoint</h2>
 * <ul>
 *   <li><b>Protegido (default):</b> no hay que tocar nada. Cualquier ruta nueva queda autenticada.</li>
 *   <li><b>Por rol:</b> {@code @PreAuthorize("hasRole('ADMIN')")} en el método del controller (o
 *       {@code hasAnyRole('ADMIN','REVIEWER')}). Sin token da 401; con otro rol, 403.</li>
 *   <li><b>Usuario actual:</b> parámetro {@code @AuthenticationPrincipal CurrentUser user}.</li>
 *   <li><b>Público:</b> sumarlo a {@link #PUBLIC_ANY} o {@link #PUBLIC_GET}. Hacerlo solo con motivo.</li>
 * </ul>
 * La prioridad y el rol no se leen del token: el rol se carga de la base por request y la prioridad se
 * consulta en el servidor al reservar (ADR 0009).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    static final String[] PUBLIC_ANY = {
            "/api/ping", "/api/auth/**", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**",
            "/actuator/health/**", "/h2-console/**"
    };
    static final String[] PUBLIC_GET = {"/api/elevators", "/api/buildings"};

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            Converter<Jwt, AbstractAuthenticationToken> converter,
                                            AuthenticationEntryPoint entryPoint,
                                            AccessDeniedHandler deniedHandler) {
        http
                .cors(withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .headers(h -> h.frameOptions(f -> f.sameOrigin())) // consola de H2 en dev
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_ANY).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler));
        return http.build();
    }

    /** 401 con el {@code ApiError} de siempre: se delega en {@code GlobalExceptionHandler}. */
    @Bean
    AuthenticationEntryPoint apiAuthenticationEntryPoint(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        return (req, res, ex) -> {
            res.setHeader("WWW-Authenticate", "Bearer");
            resolver.resolveException(req, res, null, ex);
        };
    }

    @Bean
    AccessDeniedHandler apiAccessDeniedHandler(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        return (req, res, ex) -> resolver.resolveException(req, res, null, ex);
    }
}
