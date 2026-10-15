package com.smartelevate.common.security;

/** Pedido a un endpoint que usa la cookie del refresh sin el header CSRF o desde un origen no permitido. */
public class CsrfRejectedException extends RuntimeException {

    public CsrfRejectedException() {
        super("Pedido rechazado por seguridad");
    }
}
