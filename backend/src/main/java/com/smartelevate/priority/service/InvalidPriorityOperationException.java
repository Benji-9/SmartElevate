package com.smartelevate.priority.service;

/** La operación no corresponde al estado actual (se responde 409). */
public class InvalidPriorityOperationException extends RuntimeException {
    public InvalidPriorityOperationException(String message) {
        super(message);
    }
}
