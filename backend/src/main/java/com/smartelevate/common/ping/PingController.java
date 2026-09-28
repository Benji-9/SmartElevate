package com.smartelevate.common.ping;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "ping", description = "Verificación de conectividad")
public class PingController {

    @GetMapping("/ping")
    @Operation(summary = "Verifica que la API responde")
    public PingResponse ping() {
        return new PingResponse("ok");
    }

    public record PingResponse(String status) {
    }
}
