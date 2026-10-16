package com.smartelevate.user.service;

/** Email o contraseña incorrectos. Es el mismo error para "no existe" y "clave mal": no revela si el email tiene cuenta. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Email o contraseña incorrectos");
    }
}
