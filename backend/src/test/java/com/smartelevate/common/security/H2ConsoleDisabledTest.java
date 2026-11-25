package com.smartelevate.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartelevate.common.config.CorsProperties;
import com.smartelevate.common.ping.PingController;
import com.smartelevate.common.time.ClockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Sin consola de H2 habilitada (como en prod) la ruta exige sesión y ninguna página se puede embeber (#345). */
@WebMvcTest(PingController.class)
@EnableConfigurationProperties(CorsProperties.class)
@Import(ClockConfig.class)
@WithAppSecurity
// application.yml activa el perfil dev por defecto, que prende la consola: acá se la apaga a propósito, como en prod.
@TestPropertySource(properties = "spring.h2.console.enabled=false")
class H2ConsoleDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void theConsoleRouteNeedsASession() throws Exception {
        mockMvc.perform(get("/h2-console/")).andExpect(status().isUnauthorized());
    }

    @Test
    void pagesCannotBeFramed() throws Exception {
        mockMvc.perform(get("/api/ping")).andExpect(header().string("X-Frame-Options", "DENY"));
    }
}
