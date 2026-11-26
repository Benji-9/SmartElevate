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
import org.springframework.test.web.servlet.MockMvc;

/** Fuera de dev la consola de H2 no es pública y nadie puede enmarcar la API. */
@WebMvcTest(controllers = PingController.class, properties = "spring.h2.console.enabled=false")
@EnableConfigurationProperties(CorsProperties.class)
@Import(ClockConfig.class)
@WithAppSecurity
class H2ConsoleClosedTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void h2ConsoleIsClosedWhenDisabled() throws Exception {
        mockMvc.perform(get("/h2-console/")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/ping"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }
}
