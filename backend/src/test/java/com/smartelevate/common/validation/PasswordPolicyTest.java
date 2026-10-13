package com.smartelevate.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartelevate.user.dto.RegisterRequest;
import com.smartelevate.user.model.DeclaredUserType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean accepts(String password) {
        var request = new RegisterRequest("Ana Pérez", "ana@uade.edu.ar", "1234567", password, DeclaredUserType.STUDENT);
        return validator.validateProperty(request, "password").isEmpty();
    }

    @Test
    void minimumLengthBoundary() {
        assertThat(accepts("a".repeat(PasswordPolicy.MIN - 1))).isFalse();
        assertThat(accepts("a".repeat(PasswordPolicy.MIN))).isTrue();
    }

    @Test
    void maximumLengthBoundary() {
        assertThat(accepts("a".repeat(PasswordPolicy.MAX))).isTrue();
        assertThat(accepts("a".repeat(PasswordPolicy.MAX + 1))).isFalse();
    }

    @Test
    void maximumIsInBytesNotCharacters() {
        // 36 "ñ" son 72 bytes (entra); 37 son 74 (no entra)
        assertThat(accepts("ñ".repeat(36))).isTrue();
        assertThat(accepts("ñ".repeat(37))).isFalse();
    }

    @Test
    void doesNotRequireSymbolsOrUppercase() {
        assertThat(accepts("solominusculas")).isTrue();
    }
}
