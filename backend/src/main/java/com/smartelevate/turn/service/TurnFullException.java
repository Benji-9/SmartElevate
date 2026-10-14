package com.smartelevate.turn.service;

/** La salida no tiene lugares libres. */
public class TurnFullException extends RuntimeException {

    public TurnFullException() {
        this("La salida está llena. Elegí otra.");
    }

    public TurnFullException(String message) {
        super(message);
    }
}
