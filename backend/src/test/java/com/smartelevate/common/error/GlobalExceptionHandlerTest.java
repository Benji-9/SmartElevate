package com.smartelevate.common.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Errores HTTP genéricos, con un controller de prueba: ningún endpoint real exige hoy un header. */
class GlobalExceptionHandlerTest {

    @RestController
    static class ProbeController {

        @GetMapping("/probe/header")
        public String header(@RequestHeader("X-Probe") String value) {
            return value;
        }

        @GetMapping("/probe/integrity")
        public String integrity() {
            throw new DataIntegrityViolationException("Key (email)=(ana@uade.edu.ar) already exists");
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
            .setControllerAdvice(new GlobalExceptionHandler(
                    Clock.fixed(Instant.parse("2026-11-26T12:00:00Z"), ZoneOffset.UTC)))
            .build();

    @Test
    void missingRequiredHeaderReturns400() throws Exception {
        mockMvc.perform(get("/probe/header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.violations[0].field").value("X-Probe"));
    }

    @Test
    void dataIntegrityViolationReturns409() throws Exception {
        mockMvc.perform(get("/probe/integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.path").value("/probe/integrity"))
                // El detalle de la base (con datos de la fila) no sale en la respuesta.
                .andExpect(content().string(not(containsString("ana@uade.edu.ar"))));
    }
}
