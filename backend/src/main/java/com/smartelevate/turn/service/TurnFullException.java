package com.smartelevate.turn.service;

/** La salida no tiene lugares libres. */
public class TurnFullException extends RuntimeException {

    public TurnFullException() {
        super("La salida está llena. Elegí otra.");
    }
}
