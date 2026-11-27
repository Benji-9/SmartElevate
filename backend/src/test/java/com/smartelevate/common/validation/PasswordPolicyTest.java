package com.smartelevate.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartelevate.user.dto.RegisterRequest;
import com.smartelevate.user.model.DeclaredUserType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /** {@code length} caracteres que no están en la lista de contraseñas comunes. */
    private static String password(int length) {
        return "zq".repeat(length).substring(0, length);
    }

    private Set<ConstraintViolation<RegisterRequest>> validate(String password) {
        var request = new RegisterRequest("Ana Pérez", "ana@uade.edu.ar", "1234567", password, DeclaredUserType.STUDENT);
        return validator.validateProperty(request, "password");
    }

    @Test
    void lengthBoundariesInCharacters() {
        assertThat(PasswordPolicy.violation(password(7))).isEqualTo(PasswordPolicy.SIZE_MESSAGE);
        assertThat(PasswordPolicy.violation(password(8))).isNull();
        assertThat(PasswordPolicy.violation(password(64))).isNull();
        assertThat(PasswordPolicy.violation(password(65))).isEqualTo(PasswordPolicy.SIZE_MESSAGE);
    }

    @Test
    void countsCodePointsNotBytesNorUtf16Units() {
        // "ñ": 2 bytes en UTF-8. Emoji 🛗: 4 bytes y 2 unidades UTF-16 (String.length() daría el doble).
        assertThat(PasswordPolicy.violation("ñ".repeat(64))).isNull();
        assertThat(PasswordPolicy.violation("ñ".repeat(65))).isEqualTo(PasswordPolicy.SIZE_MESSAGE);
        assertThat(PasswordPolicy.violation("🛗".repeat(8))).isNull();
        assertThat(PasswordPolicy.violation("🛗".repeat(64))).isNull();
        assertThat(PasswordPolicy.violation("🛗".repeat(65))).isEqualTo(PasswordPolicy.SIZE_MESSAGE);
        assertThat(PasswordPolicy.violation("🛗".repeat(7))).isEqualTo(PasswordPolicy.SIZE_MESSAGE);
    }

    @Test
    void countsAfterNfkc() {
        // n + tilde combinable son 2 code points, pero NFKC los compone en una sola "ñ".
        String decomposed = "ñ";
        assertThat(PasswordPolicy.normalize(decomposed)).isEqualTo("ñ");
        assertThat(PasswordPolicy.violation(decomposed.repeat(64))).isNull();
        assertThat(PasswordPolicy.violation(decomposed.repeat(4))).isEqualTo(PasswordPolicy.SIZE_MESSAGE);
        // La ligadura "ﬁ" es un carácter que NFKC convierte en dos: "fi".
        assertThat(PasswordPolicy.normalize("ﬁ")).isEqualTo("fi");
        assertThat(PasswordPolicy.violation("ﬁ".repeat(4))).isNull();
    }

    @Test
    void theLoginCapLetsInEverythingRegistrationAccepts() {
        // Peor caso de NFKC: alfa y tres marcas (4 caracteres) que se componen en uno solo.
        String alphaWithMarks = new String(new int[] {0x3B1, 0x313, 0x300, 0x345}, 0, 4);
        String longest = alphaWithMarks.repeat(PasswordPolicy.MAX);
        assertThat(longest).hasSize(PasswordPolicy.MAX * 4);
        assertThat(PasswordPolicy.violation(longest)).isNull();
        assertThat(PasswordPolicy.exceedsMax(longest)).isFalse();
        assertThat(PasswordPolicy.exceedsMax("🛗".repeat(PasswordPolicy.MAX))).isFalse();
        assertThat(PasswordPolicy.exceedsMax("a".repeat(PasswordPolicy.MAX + 1))).isTrue();
        assertThat(PasswordPolicy.exceedsMax("a".repeat(100_000))).isTrue();
        // El login no exige el mínimo ni mira la lista de comunes.
        assertThat(PasswordPolicy.exceedsMax("x")).isFalse();
    }

    @Test
    void composedAndDecomposedFormsHashTheSame() {
        assertThat(PasswordPolicy.normalize("contraseña-larga"))
                .isEqualTo(PasswordPolicy.normalize("contraseña-larga"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"password", "PASSWORD", "12345678", "qwertyuiop", "contraseña", "Contraseña",
            "uade2026", "ascensor", "smartelevate", "ＰＡＳＳＷＯＲＤ"})
    void rejectsCommonPasswordsIgnoringCaseAndUnicodeForm(String common) {
        // La última va en letras de ancho completo: NFKC la vuelve "PASSWORD".
        assertThat(PasswordPolicy.violation(common)).isEqualTo(PasswordPolicy.COMMON_MESSAGE);
    }

    @Test
    void doesNotRequireSymbolsOrUppercase() {
        assertThat(PasswordPolicy.violation("solominusculas")).isNull();
        assertThat(PasswordPolicy.violation("el ascensor del piso 7 tarda")).isNull();
    }

    @Test
    void theAnnotationReportsOneMessagePerProblem() {
        assertThat(validate(password(8))).isEmpty();
        assertThat(validate(null)).extracting(ConstraintViolation::getMessage).containsExactly("Elegí una contraseña.");
        assertThat(validate(password(7))).extracting(ConstraintViolation::getMessage)
                .containsExactly(PasswordPolicy.SIZE_MESSAGE);
        assertThat(validate("Password")).extracting(ConstraintViolation::getMessage)
                .containsExactly(PasswordPolicy.COMMON_MESSAGE);
    }
}
