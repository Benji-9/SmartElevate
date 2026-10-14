package com.smartelevate.common.security;

import org.springframework.security.core.AuthenticationException;

/** Sin credencial de kiosco, inexistente o revocada. No distingue cuál. */
public class KioskAuthenticationException extends AuthenticationException {

    public KioskAuthenticationException() {
        super("La credencial del kiosco no es válida o fue revocada");
    }
}
