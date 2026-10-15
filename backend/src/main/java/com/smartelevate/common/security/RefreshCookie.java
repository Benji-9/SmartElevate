package com.smartelevate.common.security;

import com.smartelevate.common.config.CorsProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookie HttpOnly del refresh token y su defensa CSRF (ADR 0019 y 0023). Solo {@code /auth/refresh} y
 * {@code /auth/logout} la leen, y antes de leerla pasan por {@link #requireSameSiteRequest}.
 */
@Component
@RequiredArgsConstructor
public class RefreshCookie {

    /** Header que un formulario o un pedido cross-site simple no puede mandar sin preflight CORS. */
    public static final String CSRF_HEADER = "X-Requested-With";
    public static final String CSRF_VALUE = "SmartElevate";

    private final RefreshCookieProperties props;
    private final JwtProperties jwt;
    private final CorsProperties cors;

    /** Valor del {@code Set-Cookie} que instala el refresh token, con vida igual a la del token. */
    public String issue(String refreshToken) {
        return build(refreshToken, jwt.refreshTtl());
    }

    /** Valor del {@code Set-Cookie} que borra la cookie. */
    public String expire() {
        return build("", Duration.ZERO);
    }

    private String build(String value, Duration maxAge) {
        return ResponseCookie.from(props.name(), value).httpOnly(true).secure(props.secure())
                .sameSite(props.sameSite()).path(props.path()).maxAge(maxAge).build().toString();
    }

    /** El refresh token de la cookie, o {@code null} si no vino. */
    public String read(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie c : request.getCookies()) {
            if (props.name().equals(c.getName()) && !c.getValue().isBlank()) {
                return c.getValue();
            }
        }
        return null;
    }

    /**
     * Exige el header custom y, si el navegador mandó {@code Origin}, que sea uno de los permitidos.
     *
     * @throws CsrfRejectedException si falta el header o el origen no está permitido
     */
    public void requireSameSiteRequest(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (!CSRF_VALUE.equals(request.getHeader(CSRF_HEADER))
                || (origin != null && !cors.allowedOrigins().contains(origin))) {
            throw new CsrfRejectedException();
        }
    }
}
