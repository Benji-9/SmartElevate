package com.smartelevate.user.service;

/** Refresh token desconocido, vencido, revocado o ya rotado. */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("La sesión no es válida. Volvé a iniciar sesión");
    }
}
