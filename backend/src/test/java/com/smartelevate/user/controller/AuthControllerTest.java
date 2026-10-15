package com.smartelevate.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartelevate.common.config.CorsProperties;
import com.smartelevate.common.time.ClockConfig;
import com.smartelevate.user.dto.RegisterRequest;
import com.smartelevate.user.model.DeclaredUserType;
import com.smartelevate.user.service.DuplicateUserException;
import com.smartelevate.user.service.EmailVerificationService;
import com.smartelevate.user.service.InvalidTokenException;
import com.smartelevate.user.service.ResendTooSoonException;
import java.time.Duration;
import com.smartelevate.user.service.UserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(AuthController.class)
@EnableConfigurationProperties({CorsProperties.class, com.smartelevate.common.security.JwtProperties.class,
        com.smartelevate.common.security.RefreshCookieProperties.class})
@Import({ClockConfig.class, com.smartelevate.common.security.RefreshCookie.class})
@com.smartelevate.common.security.WithAppSecurity
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService service;

    @MockitoBean
    private com.smartelevate.user.service.AuthService authService;

    @MockitoBean
    private EmailVerificationService emailVerification;

    private ResultActions register(String json) throws Exception {
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String json(String fullName, String email, String legajo, String password) {
        return """
                {"fullName":"%s","email":"%s","legajo":"%s","password":"%s","declaredUserType":"STUDENT"}"""
                .formatted(fullName, email, legajo, password);
    }

    private static String validJson(String extraFields) {
        return """
                {"fullName":"Juana Martínez","email":"jmartinez@uade.edu.ar","legajo":"1234567",
                 "password":"una-clave-larga","declaredUserType":"STUDENT"%s}""".formatted(extraFields);
    }

    @Test
    void createsTheAccountAndRespondsWithoutBody() throws Exception {
        register(validJson(""))
                .andExpect(status().isCreated())
                .andExpect(content().string(""));

        ArgumentCaptor<RegisterRequest> sent = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(service).register(sent.capture());
        assertThat(sent.getValue().fullName()).isEqualTo("Juana Martínez");
        assertThat(sent.getValue().email()).isEqualTo("jmartinez@uade.edu.ar");
        assertThat(sent.getValue().legajo()).isEqualTo("1234567");
        assertThat(sent.getValue().password()).isEqualTo("una-clave-larga");
        assertThat(sent.getValue().declaredUserType()).isEqualTo(DeclaredUserType.STUDENT);
    }

    @Test
    void ignoresRoleAndPriorityIfTheyComeInTheBody() throws Exception {
        register(validJson(",\"role\":\"ADMIN\",\"priority\":\"TEACHER\""))
                .andExpect(status().isCreated());

        verify(service).register(any(RegisterRequest.class));
    }

    @Test
    void acceptsTheDomainInAnyCase() throws Exception {
        register(json("Juana", "Juana.Martinez+tp@UADE.EDU.AR", "1234567", "una-clave-larga"))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"juana@gmail.com", "juana@uade.edu.ar.evil.com", "@uade.edu.ar", "juana smith@uade.edu.ar",
            "juana@sub.uade.edu.ar", ""})
    void rejectsEmailsThatAreNotFromUade(String email) throws Exception {
        register(json("Juana", email, "1234567", "una-clave-larga"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.length()").value(1))
                .andExpect(jsonPath("$.violations[0].field").value("email"))
                .andExpect(jsonPath("$.violations[0].message").value("Usá tu email @uade.edu.ar."));
        verify(service, never()).register(any());
    }

    @Test
    void reportsEveryMissingField() throws Exception {
        register("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[*].field").value(containsInAnyOrder(
                        "fullName", "email", "legajo", "password", "declaredUserType")));
        verify(service, never()).register(any());
    }

    @Test
    void rejectsABlankName() throws Exception {
        register(json("   ", "jmartinez@uade.edu.ar", "1234567", "una-clave-larga"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("fullName"))
                .andExpect(jsonPath("$.violations[0].message").value("Ingresá tu nombre y apellido."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"12a45", "12 34", "12345678901", ""})
    void rejectsALegajoThatIsNotANumberOfUpTo10Digits(String legajo) throws Exception {
        register(json("Juana", "jmartinez@uade.edu.ar", legajo, "una-clave-larga"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.length()").value(1))
                .andExpect(jsonPath("$.violations[0].field").value("legajo"));
    }

    /** {@code length} caracteres que no están en la lista de contraseñas comunes. */
    private static String password(int length) {
        return "zq".repeat(length).substring(0, length);
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 64})
    void acceptsPasswordsOfBetween8And64Characters(int length) throws Exception {
        register(json("Juana", "jmartinez@uade.edu.ar", "1234567", password(length)))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 7, 65})
    void rejectsAPasswordOutsideTheLimitsWithASingleError(int length) throws Exception {
        register(json("Juana", "jmartinez@uade.edu.ar", "1234567", password(length)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.length()").value(1))
                .andExpect(jsonPath("$.violations[0].field").value("password"))
                .andExpect(jsonPath("$.violations[0].message")
                        .value("La contraseña tiene que tener entre 8 y 64 caracteres."));
    }

    @Test
    void countsCharactersNotBytes() throws Exception {
        // 64 ñ son 128 bytes en UTF-8, y 16 emojis son 32 unidades UTF-16: los dos son caracteres válidos.
        register(json("Juana", "jmartinez@uade.edu.ar", "1234567", "ñ".repeat(64))).andExpect(status().isCreated());
        register(json("Juana", "jmartinez@uade.edu.ar", "1234567", "🛗".repeat(16))).andExpect(status().isCreated());
        register(json("Juana", "jmartinez@uade.edu.ar", "1234567", "ñ".repeat(65)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("password"));
    }

    @Test
    void rejectsACommonPasswordWhateverTheCase() throws Exception {
        register(json("Juana", "jmartinez@uade.edu.ar", "1234567", "Contraseña123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.length()").value(1))
                .andExpect(jsonPath("$.violations[0].field").value("password"))
                .andExpect(jsonPath("$.violations[0].message").value(startsWith("Esa contraseña es muy común")));
        verify(service, never()).register(any());
    }

    @Test
    void rejectsAnInventedUserType() throws Exception {
        register(validJson("").replace("STUDENT", "ADMIN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El cuerpo de la solicitud es inválido"));
        verify(service, never()).register(any());
    }

    @Test
    void answers409WithTheFieldsThatCollided() throws Exception {
        willThrow(new DuplicateUserException(List.of("email", "legajo"))).given(service).register(any());

        register(validJson(""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Ya existe una cuenta con esos datos"))
                .andExpect(jsonPath("$.path").value("/api/auth/register"))
                .andExpect(jsonPath("$.violations[0].field").value("email"))
                .andExpect(jsonPath("$.violations[0].message").value("Ya hay una cuenta con ese email."))
                .andExpect(jsonPath("$.violations[1].field").value("legajo"))
                .andExpect(jsonPath("$.violations[1].message").value("Ya hay una cuenta con ese legajo."));
    }

    private ResultActions postJson(String path, String json) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void verifyRespondsNoContentAndPassesTheToken() throws Exception {
        postJson("/api/auth/verify", "{\"token\":\"abc\"}").andExpect(status().isNoContent());

        verify(emailVerification).verify("abc");
    }

    @Test
    void verifyRequiresAToken() throws Exception {
        postJson("/api/auth/verify", "{\"token\":\" \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("token"));
        verify(emailVerification, never()).verify(any());
    }

    @Test
    void verifyWithAnUsedOrExpiredTokenIsABadRequestWithTheReason() throws Exception {
        willThrow(new InvalidTokenException(InvalidTokenException.Reason.USED)).given(emailVerification).verify("abc");

        postJson("/api/auth/verify", "{\"token\":\"abc\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Este link ya se usó."));

        willThrow(new InvalidTokenException(InvalidTokenException.Reason.EXPIRED)).given(emailVerification)
                .verify("old");
        postJson("/api/auth/verify", "{\"token\":\"old\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El link venció. Pedí uno nuevo."));
    }

    @Test
    void resendRespondsAccepted() throws Exception {
        postJson("/api/auth/resend-verification", "{\"email\":\"jmartinez@uade.edu.ar\"}")
                .andExpect(status().isAccepted());

        verify(emailVerification).resend("jmartinez@uade.edu.ar");
    }

    @Test
    void resendTooSoonIs429WithRetryAfter() throws Exception {
        willThrow(new ResendTooSoonException(Duration.ofSeconds(42))).given(emailVerification).resend(any());

        postJson("/api/auth/resend-verification", "{\"email\":\"jmartinez@uade.edu.ar\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "42"))
                .andExpect(jsonPath("$.status").value(429));
    }

    // ---- reset de contraseña

    @MockitoBean
    private com.smartelevate.user.service.PasswordResetService passwordReset;

    @Test
    void forgotRespondsAcceptedWithNoBody() throws Exception {
        postJson("/api/auth/password/forgot", "{\"email\":\"jmartinez@uade.edu.ar\"}")
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        verify(passwordReset).request("jmartinez@uade.edu.ar");
    }

    @Test
    void forgotRequiresAnEmail() throws Exception {
        postJson("/api/auth/password/forgot", "{\"email\":\" \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("email"));
    }

    @Test
    void resetRespondsNoContent() throws Exception {
        postJson("/api/auth/password/reset", "{\"token\":\"tok\",\"newPassword\":\"una-clave-nueva\"}")
                .andExpect(status().isNoContent());

        verify(passwordReset).reset("tok", "una-clave-nueva");
    }

    @Test
    void resetAppliesThePasswordPolicy() throws Exception {
        postJson("/api/auth/password/reset", "{\"token\":\"tok\",\"newPassword\":\"corta\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("newPassword"))
                .andExpect(content().string(not(containsString("corta\""))));
        postJson("/api/auth/password/reset", "{\"token\":\"tok\",\"newPassword\":\"" + password(65) + "\"}")
                .andExpect(status().isBadRequest());
        postJson("/api/auth/password/reset", "{\"token\":\"tok\",\"newPassword\":\"qwertyuiop\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].message").value(startsWith("Esa contraseña es muy común")));
        postJson("/api/auth/password/reset", "{\"token\":\"tok\",\"newPassword\":\"" + "ñ".repeat(64) + "\"}")
                .andExpect(status().isNoContent());

        // Solo llegó al servicio la que cumple la política.
        verify(passwordReset).reset(any(), any());
        verify(passwordReset).reset("tok", "ñ".repeat(64));
    }

    @Test
    void resetWithABadTokenIs400() throws Exception {
        willThrow(new InvalidTokenException(InvalidTokenException.Reason.USED)).given(passwordReset)
                .reset("tok", "una-clave-nueva");

        postJson("/api/auth/password/reset", "{\"token\":\"tok\",\"newPassword\":\"una-clave-nueva\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Este link ya se usó."));
    }

    @Test
    void theResetRequestToStringHidesTokenAndPassword() {
        String text = new com.smartelevate.user.dto.ResetPasswordRequest("tok-secreto", "clave-secreta-123").toString();

        assertThat(text).doesNotContain("tok-secreto").doesNotContain("clave-secreta-123");
    }

    @Test
    void neverEchoesThePasswordBackInAnErrorResponse() throws Exception {
        register(json("Juana", "no-es-de-uade@gmail.com", "1234567", "pw-7ch"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("pw-7ch"))));
    }

    @Test
    void theRequestToStringHidesThePassword() {
        RegisterRequest request = new RegisterRequest("Juana", "jmartinez@uade.edu.ar", "1234567",
                "clave-secreta-123", DeclaredUserType.STUDENT);

        assertThat(request.toString()).contains("password=***").doesNotContain("clave-secreta-123");
    }

    // ---- sesión

    @Test
    void loginReturnsTheAccessTokenAndSetsTheRefreshCookie() throws Exception {
        org.mockito.BDDMockito.given(authService.login(eq("ana@uade.edu.ar"), eq("una-clave-larga"), any()))
                .willReturn(new com.smartelevate.user.dto.SessionTokens(
                        new com.smartelevate.user.dto.TokenResponse("acc", "Bearer", 900), "ref"));

        postJson("/api/auth/login", "{\"email\":\"ana@uade.edu.ar\",\"password\":\"una-clave-larga\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("acc"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(header().string("Set-Cookie", startsWith("refresh_token=ref;")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/api/auth")));
    }

    @Test
    void badCredentialsAre401WithTheStandardErrorBody() throws Exception {
        org.mockito.BDDMockito.given(authService.login(any(), any(), any()))
                .willThrow(new com.smartelevate.user.service.InvalidCredentialsException());

        postJson("/api/auth/login", "{\"email\":\"a@uade.edu.ar\",\"password\":\"x\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));
    }

    @Test
    void unverifiedEmailIs403() throws Exception {
        org.mockito.BDDMockito.given(authService.login(any(), any(), any()))
                .willThrow(new com.smartelevate.user.service.EmailNotVerifiedException());

        postJson("/api/auth/login", "{\"email\":\"a@uade.edu.ar\",\"password\":\"x\"}")
                .andExpect(status().isForbidden());
    }

    @Test
    void tooManyFailedLoginsIs429WithRetryAfterAndTheClientIp() throws Exception {
        org.mockito.BDDMockito.given(authService.login(any(), any(), eq("198.51.100.4")))
                .willThrow(new com.smartelevate.user.service.TooManyLoginAttemptsException(Duration.ofMillis(89_001)));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@uade.edu.ar\",\"password\":\"x\"}")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.4");
                            return request;
                        }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "90"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("Hubo demasiados intentos fallidos. Probá de nuevo en 2 minutos."));
    }

    @Test
    void loginValidatesTheBody() throws Exception {
        postJson("/api/auth/login", "{\"email\":\"\"}").andExpect(status().isBadRequest());
    }

    private ResultActions refreshWith(String cookie) throws Exception {
        var req = post("/api/auth/refresh").header("X-Requested-With", "SmartElevate");
        return mockMvc.perform(cookie == null ? req : req.cookie(new jakarta.servlet.http.Cookie("refresh_token", cookie)));
    }

    @Test
    void refreshReadsTheCookieAndRotatesIt() throws Exception {
        org.mockito.BDDMockito.given(authService.refresh("viejo")).willReturn(new com.smartelevate.user.dto.SessionTokens(
                new com.smartelevate.user.dto.TokenResponse("acc2", "Bearer", 900), "nuevo"));

        refreshWith("viejo").andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").value("acc2"))
                .andExpect(header().string("Set-Cookie", startsWith("refresh_token=nuevo;")));
    }

    @Test
    void invalidOrMissingRefreshIs401() throws Exception {
        org.mockito.BDDMockito.given(authService.refresh("viejo"))
                .willThrow(new com.smartelevate.user.service.InvalidRefreshTokenException());

        refreshWith("viejo").andExpect(status().isUnauthorized());
        refreshWith(null).andExpect(status().isUnauthorized());
        verify(authService, never()).refresh("");
    }

    @Test
    void refreshAndLogoutNeedTheCsrfHeaderAndAnAllowedOrigin() throws Exception {
        var cookie = new jakarta.servlet.http.Cookie("refresh_token", "viejo");

        mockMvc.perform(post("/api/auth/refresh").cookie(cookie)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mockMvc.perform(post("/api/auth/logout").cookie(cookie)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/logout").cookie(cookie).header("X-Requested-With", "SmartElevate")
                .header("Origin", "https://evil.example")).andExpect(status().isForbidden());
        verify(authService, never()).refresh(any());
        verify(authService, never()).logout(any());
    }

    @Test
    void logoutRevokesAndExpiresTheCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout").header("X-Requested-With", "SmartElevate")
                        .header("Origin", "http://localhost:5173")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "viejo")))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
        verify(authService).logout("viejo");
    }
}
