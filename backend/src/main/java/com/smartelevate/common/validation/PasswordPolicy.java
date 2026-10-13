package com.smartelevate.common.validation;

/**
 * Política de contraseña (docs/reglas/usuarios-y-prioridad.md#contraseña). Constantes para las anotaciones del DTO
 * de registro y del de contraseña nueva (#138):
 * {@code @Size(min = MIN, max = MAX, message = SIZE_MESSAGE) @MaxUtf8Bytes(value = MAX, message = BYTES_MESSAGE)}.
 */
public final class PasswordPolicy {

    public static final int MIN = 8;
    /** Límite de BCrypt, en bytes; también se usa como tope de caracteres. */
    public static final int MAX = 72;
    public static final String SIZE_MESSAGE = "La contraseña tiene que tener entre " + MIN + " y " + MAX + " caracteres.";
    public static final String BYTES_MESSAGE =
            "La contraseña es demasiado larga: el máximo son " + MAX + " bytes y la ñ o las tildes ocupan 2.";

    private PasswordPolicy() {}
}
