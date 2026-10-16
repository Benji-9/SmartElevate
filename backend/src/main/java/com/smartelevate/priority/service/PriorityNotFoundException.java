package com.smartelevate.priority.service;

/** El usuario no existe o no tiene lo que se quiere cambiar (se responde 404). */
public class PriorityNotFoundException extends RuntimeException {
    public PriorityNotFoundException(String message) {
        super(message);
    }
}
