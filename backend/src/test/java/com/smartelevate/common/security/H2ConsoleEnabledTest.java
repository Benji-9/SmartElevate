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

/** Con la consola de H2 habilitada (perfil dev) la ruta queda libre y se permite el iframe del mismo origen (#345). */
@WebMvcTest(PingController.class)
@EnableConfigurationProperties(CorsProperties.class)
@Import(ClockConfig.class)
@WithAppSecurity
@TestPropertySource(properties = "spring.h2.console.enabled=true")
class H2ConsoleEnabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void theConsoleRouteIsNotBlockedBySecurity() throws Exception {
        // No da 401: el filtro la deja pasar. En este slice no hay servlet de H2, de ahí el 404.
        mockMvc.perform(get("/h2-console/")).andExpect(status().isNotFound());
    }

    @Test
    void pagesCanOnlyBeFramedBySameOrigin() throws Exception {
        mockMvc.perform(get("/api/ping")).andExpect(header().string("X-Frame-Options", "SAMEORIGIN"));
    }
}
