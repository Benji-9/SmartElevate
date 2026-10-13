package com.smartelevate.user.dto;

import com.smartelevate.common.validation.MaxUtf8Bytes;
import com.smartelevate.common.validation.PasswordPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Token del link del mail y la contraseña nueva")
public record ResetPasswordRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Zk3pQ1x9...")
        @NotBlank(message = "Falta el token.")
        String token,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "password", example = "una-clave-nueva",
                description = "Misma política que el registro: entre 8 y 72 caracteres y hasta 72 bytes")
        @NotNull(message = "Elegí una contraseña.")
        @Size(min = PasswordPolicy.MIN, max = PasswordPolicy.MAX, message = PasswordPolicy.SIZE_MESSAGE)
        @MaxUtf8Bytes(value = PasswordPolicy.MAX, message = PasswordPolicy.BYTES_MESSAGE)
        String newPassword) {

    /** Sin token ni contraseña: no deben aparecer en logs. */
    @Override
    public String toString() {
        return "ResetPasswordRequest[token=***, newPassword=***]";
    }
}
