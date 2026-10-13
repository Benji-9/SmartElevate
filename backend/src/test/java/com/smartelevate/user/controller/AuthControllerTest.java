package com.smartelevate.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
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
@EnableConfigurationProperties(CorsProperties.class)
@Import(ClockConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService service;

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

    @ParameterizedTest
    @ValueSource(ints = {8, 72})
    void acceptsPasswordsOfBetween8And72Characters(int length) throws Exception {
        register(json("Juana", "jmartinez@uade.edu.ar", "1234567", "a".repeat(length)))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 7})
    void rejectsAPasswordShorterThan8Characters(int length) throws Exception {
        register(json("Juana", "jmartinez@uade.edu.ar", "1234567", "a".repeat(length)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.length()").value(1))
                .andExpect(jsonPath("$.violations[0].field").value("password"))
                .andExpect(jsonPath("$.violations[0].message")
                        .value("La contraseña tiene que tener entre 8 y 72 caracteres."));
    }

    @Test
    void rejectsAPasswordLongerThan72CharactersWithASingleError() throws Exception {
        register(json("Juana", "jmartinez@uade.edu.ar", "1234567", "a".repeat(73)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.length()").value(1))
                .andExpect(jsonPath("$.violations[0].field").value("password"))
                .andExpect(jsonPath("$.violations[0].message")
                        .value("La contraseña tiene que tener entre 8 y 72 caracteres."));
    }

    @Test
    void acceptsAPasswordThatFillsExactlyTheLimitOf72Bytes() throws Exception {
        // 36 ñ = 36 caracteres pero 72 bytes en UTF-8.
        register(json("Juana", "jmartinez@uade.edu.ar", "1234567", "ñ".repeat(36)))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsAPasswordWithFewCharactersButMoreThan72Bytes() throws Exception {
        // 37 ñ = 37 caracteres (entra en @Size) pero 74 bytes: BCrypt tiraría error si llegara a hashearla.
        register(json("Juana", "jmartinez@uade.edu.ar", "1234567", "ñ".repeat(37)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.length()").value(1))
                .andExpect(jsonPath("$.violations[0].field").value("password"))
                .andExpect(jsonPath("$.violations[0].message").value(startsWith("La contraseña es demasiado larga")));
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
}
