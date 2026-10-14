package com.smartelevate.elevator.service;

/**
 * QR mal formado, con firma inválida, vencido o de un kiosco revocado. A propósito no dice cuál: el handler responde
 * 400 con code {@code INVALID_QR_TOKEN} y este mensaje fijo.
 */
public class InvalidQrTokenException extends RuntimeException {

    public InvalidQrTokenException() {
        super("El QR no es válido o ya venció. Escaneá el que está en pantalla.");
    }
}
