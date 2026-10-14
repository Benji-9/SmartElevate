package com.smartelevate.elevator.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;

/**
 * Variables obligatorias del kiosco en prod (#222, #229): sin {@code APP_KIOSK_QR_SECRET} (o con menos de 32
 * caracteres) la app no arranca. Solo existe en el perfil {@code prod}; en dev y tests se usa una clave efímera.
 */
@Profile("prod")
@Validated
@ConfigurationProperties(prefix = "app.kiosk")
public record KioskProdProperties(
        @NotBlank(message = "falta la variable de entorno APP_KIOSK_QR_SECRET")
        @Size(min = QrTokenService.MIN_SECRET_BYTES,
                message = "APP_KIOSK_QR_SECRET tiene que tener al menos 32 caracteres")
        String qrSecret) {
}
