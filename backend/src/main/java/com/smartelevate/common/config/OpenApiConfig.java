package com.smartelevate.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI smartElevateOpenApi() {
        return new OpenAPI().info(new Info()
                .title("SmartElevate API")
                .description("Reserva de turnos de ascensores para reducir la congestión en el campus")
                .version("v0"));
    }
}
