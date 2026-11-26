package com.smartelevate.user.dto;

import com.smartelevate.common.validation.PasswordPolicy;
import com.smartelevate.common.validation.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credenciales de inicio de sesión")
public record LoginRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "jmartinez@uade.edu.ar")
        @NotBlank(message = "Ingresá tu email.")
        String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "password", example = "una-clave-larga",
                maxLength = PasswordPolicy.MAX,
                description = "Hasta " + PasswordPolicy.MAX + " caracteres, contados como en el registro (tras NFKC)")
        @NotBlank(message = "Ingresá tu contraseña.")
        @ValidPassword(onlyMax = true)
        String password) {

    /** Sin la contraseña: con el log en DEBUG, Spring imprime el cuerpo del pedido usando este método. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***]";
    }
}
