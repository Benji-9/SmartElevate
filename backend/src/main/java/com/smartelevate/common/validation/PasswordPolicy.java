package com.smartelevate.common.validation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Política de contraseña (docs/reglas/usuarios-y-prioridad.md#contraseña, ADR 0015): entre {@link #MIN} y
 * {@link #MAX} caracteres Unicode (code points, no bytes) tras normalizar con NFKC, sin reglas de composición y
 * fuera de la lista de contraseñas comunes. La aplican el registro y el reset vía {@link ValidPassword}; el login
 * y el hash usan {@link #normalize} para que la misma clave escrita de otra forma Unicode dé el mismo hash.
 */
public final class PasswordPolicy {

    public static final int MIN = 8;
    public static final int MAX = 64;
    public static final String SIZE_MESSAGE = "La contraseña tiene que tener entre " + MIN + " y " + MAX + " caracteres.";
    public static final String COMMON_MESSAGE = "Esa contraseña es muy común y fácil de adivinar. Elegí otra.";
    /** Para el {@code @Schema} de los DTOs. */
    public static final String DESCRIPTION = "Entre " + MIN + " y " + MAX + " caracteres (se cuentan caracteres, no "
            + "bytes, tras normalizar con NFKC). Sin reglas de composición; se rechazan las contraseñas comunes";

    private static final String COMMON_LIST = "/security/common-passwords.txt";

    private PasswordPolicy() {}

    public static String normalize(String raw) {
        return Normalizer.normalize(raw, Normalizer.Form.NFKC);
    }

    /** El mensaje de error si la contraseña no cumple la política, o {@code null} si cumple. */
    public static String violation(String raw) {
        String password = normalize(raw);
        int length = password.codePointCount(0, password.length());
        if (length < MIN || length > MAX) {
            return SIZE_MESSAGE;
        }
        return CommonPasswords.SET.contains(password.toLowerCase(Locale.ROOT)) ? COMMON_MESSAGE : null;
    }

    /** Se carga la primera vez que se valida una contraseña. */
    private static final class CommonPasswords {

        static final Set<String> SET = load();

        private static Set<String> load() {
            try (InputStream in = Objects.requireNonNull(PasswordPolicy.class.getResourceAsStream(COMMON_LIST),
                    COMMON_LIST);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines()
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .map(line -> normalize(line).toLowerCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
