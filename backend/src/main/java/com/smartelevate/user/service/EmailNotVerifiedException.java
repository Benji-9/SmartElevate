package com.smartelevate.user.service;

/** Credenciales correctas, pero el email institucional todavía no se verificó. */
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException() {
        super("Verificá tu email institucional antes de iniciar sesión");
    }
}
