package com.smartelevate.user.model;

/** Para qué sirve un token enviado por mail. Un token de un propósito no vale para el otro. */
public enum TokenPurpose {
    VERIFY_EMAIL,
    PASSWORD_RESET
}
