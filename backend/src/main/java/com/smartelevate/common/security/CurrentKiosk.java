package com.smartelevate.common.security;

import java.util.UUID;

/**
 * Kiosco autenticado con su credencial de dispositivo (ADR 0020), en {@code /api/kiosks/**}. Se obtiene con
 * {@code @AuthenticationPrincipal CurrentKiosk kiosk}. El ascensor sale de la credencial, nunca del pedido.
 */
public record CurrentKiosk(UUID id, String elevatorId) {
}
