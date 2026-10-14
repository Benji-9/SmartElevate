package com.smartelevate.elevator.service;

/**
 * QR mal formado, con firma inválida, vencido o de un kiosco revocado. A propósito no dice cuál: el handler HTTP lo
 * agrega el check-in (#230).
 */
public class InvalidQrTokenException extends RuntimeException {

    public InvalidQrTokenException() {
        super("El QR no es válido o ya venció. Escaneá el que está en pantalla.");
    }
}
