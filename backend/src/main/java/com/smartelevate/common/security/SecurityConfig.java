package com.smartelevate.common.security;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
 * y {@code /actuator/health}. {@code /h2-console/**} es público solo si la consola está habilitada
 * ({@code spring.h2.console.enabled}, que solo prende el perfil dev): en prod queda cerrada y con
 * {@code X-Frame-Options: DENY}. Todo lo demás exige un access token válido.
 *
 * <h2>Cómo agregar un endpoint</h2>
 * <ul>
 *   <li><b>Protegido (default):</b> no hay que tocar nada. Cualquier ruta nueva queda autenticada.</li>
 *   <li><b>Por rol:</b> {@code @PreAuthorize("hasRole('ADMIN')")} en el método del controller (o
 *       {@code hasAnyRole('ADMIN','REVIEWER')}). Sin token da 401; con otro rol, 403.</li>
 *   <li><b>Admin:</b> todo lo que cuelga de {@code /api/admin/**} exige ADMIN ya a nivel de URL, antes de que el
 *       controller valide parámetros o cuerpo (un usuario común recibe 403, no 400). El {@code @PreAuthorize} de
 *       cada controller queda como segunda barrera.</li>
 *   <li><b>Usuario actual:</b> parámetro {@code @AuthenticationPrincipal CurrentUser user}.</li>
 *   <li><b>Público:</b> sumarlo a {@link #PUBLIC_ANY} o {@link #PUBLIC_GET}. Hacerlo solo con motivo.</li>
 * </ul>
 * La prioridad y el rol no se leen del token: el rol se carga de la base por request y la prioridad se
 * consulta en el servidor al reservar (ADR 0009).
 *
 * <p>{@code /api/kiosks/**} no pasa por acá: tiene su propia cadena con la credencial del kiosco
 * ({@link KioskSecurityConfig}, ADR 0020).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    static final String[] PUBLIC_ANY = {
            "/api/ping", "/api/auth/**", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**",
            "/actuator/health/**"
    };
    static final String[] PUBLIC_GET = {"/api/elevators", "/api/buildings"};
    /** Solo es público (y se deja en un iframe) si la consola está habilitada: hoy, solo en el perfil dev (#345). */
    static final String H2_CONSOLE = "/h2-console/**";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            Converter<Jwt, AbstractAuthenticationToken> converter,
                                            AuthenticationEntryPoint entryPoint,
                                            AccessDeniedHandler deniedHandler,
                                            @Value("${spring.h2.console.enabled:false}") boolean h2Console) {
        http
                .cors(withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                // La consola de H2 corre en un iframe del mismo origen; sin consola queda el DENY por defecto.
                .headers(h -> {
                    if (h2Console) {
                        h.frameOptions(f -> f.sameOrigin());
                    }
                })
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> {
                    a.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    a.requestMatchers(PUBLIC_ANY).permitAll();
                    if (h2Console) {
                        a.requestMatchers(H2_CONSOLE).permitAll();
                    }
                    a.requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll();
                    // El rol se mira antes de que el controller valide parámetros o cuerpo: así un usuario
                    // común recibe 403 y no un 400 que le muestre la forma del pedido (#345).
                    a.requestMatchers("/api/admin/**").hasRole("ADMIN");
                    a.anyRequest().authenticated();
                })
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
