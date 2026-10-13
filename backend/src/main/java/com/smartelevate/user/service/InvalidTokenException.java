package com.smartelevate.user.service;

import lombok.Getter;

/** El token no sirve: no existe, ya se usó o venció. */
@Getter
public class InvalidTokenException extends RuntimeException {

    public enum Reason { INVALID, USED, EXPIRED }

    private final Reason reason;

    public InvalidTokenException(Reason reason) {
        super(switch (reason) {
            case INVALID -> "El link no es válido.";
            case USED -> "Este link ya se usó.";
            case EXPIRED -> "El link venció. Pedí uno nuevo.";
        });
        this.reason = reason;
    }
}
