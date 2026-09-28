package com.smartelevate.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Formato único de error que devuelve la API.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Error estándar de la API")
public record ApiError(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant timestamp,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "404") int status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Not Found") String error,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Recurso no encontrado") String message,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "/api/turns/42") String path,
        @Schema(description = "Solo en errores de validación") List<FieldViolation> violations) {

    public record FieldViolation(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String field,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message) {
    }
}
