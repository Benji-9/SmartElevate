package com.smartelevate.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.smartelevate.common.validation.MaxUtf8Bytes;
import com.smartelevate.user.model.DeclaredUserType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Pedido de registro. No tiene campo de rol ni de prioridad a propósito (ADR 0009): si el JSON trae uno,
 * se ignora. Los mensajes son los que la pantalla de Registro muestra junto a cada campo.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Datos para crear una cuenta. No lleva rol ni prioridad: todo usuario nace común")
public record RegisterRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Juana Martínez")
        @NotBlank(message = "Ingresá tu nombre y apellido.")
        @Size(max = 100, message = "El nombre es demasiado largo.")
        String fullName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "jmartinez@uade.edu.ar",
                description = "Email institucional. Se guarda en minúsculas")
        @NotNull(message = "Ingresá tu email institucional.")
        @Pattern(regexp = "[A-Za-z0-9._%+-]{1,64}@uade\\.edu\\.ar", flags = Pattern.Flag.CASE_INSENSITIVE,
                message = "Usá tu email @uade.edu.ar.")
        String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1234567")
        @NotNull(message = "Ingresá tu legajo.")
        @Pattern(regexp = "\\d{1,10}", message = "El legajo tiene que ser numérico, de hasta 10 dígitos.")
        String legajo,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "password", example = "una-clave-larga",
                description = "Entre 8 y 72 caracteres, y hasta 72 bytes (la ñ y las tildes ocupan 2). "
                        + "Sin otras reglas")
        @NotNull(message = "Elegí una contraseña.")
        @Size(min = 8, max = 72, message = "La contraseña tiene que tener entre 8 y 72 caracteres.")
        @MaxUtf8Bytes(value = 72,
                message = "La contraseña es demasiado larga: el máximo son 72 bytes y la ñ o las tildes ocupan 2.")
        String password,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Lo que la persona declara ser. Es informativo: no da rol ni prioridad")
        @NotNull(message = "Elegí si sos estudiante o docente.")
        DeclaredUserType declaredUserType) {

    /** Sin la contraseña: con el log en DEBUG, Spring imprime el cuerpo del pedido usando este método. */
    @Override
    public String toString() {
        return "RegisterRequest[fullName=" + fullName + ", email=" + email + ", legajo=" + legajo
                + ", password=***, declaredUserType=" + declaredUserType + "]";
    }
}
