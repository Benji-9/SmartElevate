package com.smartelevate.user.controller;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartelevate.user.repository.EmailTokenRepository;
import com.smartelevate.user.repository.LoginFailureRepository;
import com.smartelevate.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * M2 (#344): tope de pedidos por IP en registro, reenvío de verificación y "olvidé mi contraseña". Con el tope real
 * (60): la config de test lo sube para el resto de las pruebas, que comparten la IP de MockMvc.
 */
@SpringBootTest(properties = "app.request-throttle.max-per-ip=60")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RequestThrottleApiTest {

    private static final int MAX = 60;
    private static final String IP = "203.0.113.10";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private EmailTokenRepository emailTokens;

    @Autowired
    private LoginFailureRepository requests;

    @BeforeEach
    @AfterEach
    void clean() {
        requests.deleteAll();
        emailTokens.deleteAll();
        users.deleteAll();
    }

    private ResultActions postFrom(String ip, String path, String body) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                }));
    }

    private ResultActions register(String ip, int n) throws Exception {
        return postFrom(ip, "/api/auth/register", """
                {"fullName":"Ana Pérez","email":"ana%d@uade.edu.ar","legajo":"%d","password":"una-clave-larga",
                 "declaredUserType":"STUDENT"}""".formatted(n, 1_000_000 + n));
    }

    private static void isRateLimited(ResultActions result) throws Exception {
        result.andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", matchesPattern("[1-9]\\d*")))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void thirtyRegistrationsFromOneIpSucceed() throws Exception {
        // Review Focus 2: muchos estudiantes detrás del NAT del campus.
        for (int i = 0; i < 30; i++) {
            register(IP, i).andExpect(status().isCreated());
        }
    }

    @Test
    void registerIsRateLimitedPerIp() throws Exception {
        for (int i = 0; i < MAX; i++) {
            register(IP, i).andExpect(status().isCreated());
        }

        isRateLimited(register(IP, MAX));
        // Otra IP no comparte el tope, y cada acción cuenta por separado.
        register("198.51.100.20", MAX).andExpect(status().isCreated());
        postFrom(IP, "/api/auth/resend-verification", "{\"email\":\"ana0@uade.edu.ar\"}")
                .andExpect(status().isAccepted());
        postFrom(IP, "/api/auth/password/forgot", "{\"email\":\"ana0@uade.edu.ar\"}")
                .andExpect(status().isAccepted());
    }

    @Test
    void resendVerificationIsRateLimitedPerIp() throws Exception {
        for (int i = 0; i < MAX; i++) {
            postFrom(IP, "/api/auth/resend-verification", "{\"email\":\"nadie@uade.edu.ar\"}")
                    .andExpect(status().isAccepted());
        }

        isRateLimited(postFrom(IP, "/api/auth/resend-verification", "{\"email\":\"nadie@uade.edu.ar\"}"));
        postFrom(IP, "/api/auth/password/forgot", "{\"email\":\"nadie@uade.edu.ar\"}").andExpect(status().isAccepted());
    }

    @Test
    void forgotPasswordIsRateLimitedPerIp() throws Exception {
        for (int i = 0; i < MAX; i++) {
            postFrom(IP, "/api/auth/password/forgot", "{\"email\":\"nadie@uade.edu.ar\"}")
                    .andExpect(status().isAccepted());
        }

        isRateLimited(postFrom(IP, "/api/auth/password/forgot", "{\"email\":\"nadie@uade.edu.ar\"}"));
        register(IP, 0).andExpect(status().isCreated());
    }
}
