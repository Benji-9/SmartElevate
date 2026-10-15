package com.smartelevate.user.dto;

import com.smartelevate.common.validation.PasswordPolicy;
import com.smartelevate.common.validation.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Token del link del mail y la contraseña nueva")
public record ResetPasswordRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Zk3pQ1x9...")
        @NotBlank(message = "Falta el token.")
        String token,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "password", example = "una-clave-nueva",
                minLength = PasswordPolicy.MIN, maxLength = PasswordPolicy.MAX,
                description = "Misma política que el registro. " + PasswordPolicy.DESCRIPTION)
        @NotNull(message = "Elegí una contraseña.")
        @ValidPassword
        String newPassword) {

    /** Sin token ni contraseña: no deben aparecer en logs. */
    @Override
    public String toString() {
        return "ResetPasswordRequest[token=***, newPassword=***]";
    }
}
