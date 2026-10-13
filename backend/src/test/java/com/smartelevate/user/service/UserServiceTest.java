package com.smartelevate.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.smartelevate.user.dto.RegisterRequest;
import com.smartelevate.user.model.DeclaredUserType;
import com.smartelevate.user.model.Role;
import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T15:00:00Z");

    private final UserRepository users = mock(UserRepository.class);
    // Costo 4 (el mínimo de BCrypt): es el algoritmo real, pero los tests no pagan los ~140 ms de cada hash.
    private final PasswordEncoder encoder = spy(new BCryptPasswordEncoder(4));
    private final EmailVerificationService emailVerification = mock(EmailVerificationService.class);
    private final UserService service =
            new UserService(users, encoder, Clock.fixed(NOW, ZoneOffset.UTC), emailVerification);

    private static RegisterRequest request(String email, String legajo) {
        return new RegisterRequest("  Juana Martínez ", email, legajo, "una-clave-larga", DeclaredUserType.TEACHER);
    }

    @Test
    void savesACommonUnverifiedAccountWithAHashedPassword() {
        service.register(request("JMartinez@UADE.edu.ar", "1234567"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        User user = saved.getValue();
        assertThat(user.getFullName()).isEqualTo("Juana Martínez");
        assertThat(user.getEmail()).isEqualTo("jmartinez@uade.edu.ar");
        assertThat(user.getLegajo()).isEqualTo("1234567");
        assertThat(user.getDeclaredUserType()).isEqualTo(DeclaredUserType.TEACHER);
        assertThat(user.getCreatedAt()).isEqualTo(NOW);
        // Aunque se declare docente, nace común y sin verificar.
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.getPasswordHash()).isNotEqualTo("una-clave-larga").startsWith("$2");
        assertThat(encoder.matches("una-clave-larga", user.getPasswordHash())).isTrue();
        verify(emailVerification).sendVerificationQuietly(user);
    }

    @Test
    void doesNotSendTheVerificationMailIfTheAccountWasNotCreated() {
        given(users.existsByEmail("jmartinez@uade.edu.ar")).willReturn(true);

        assertThatThrownBy(() -> service.register(request("jmartinez@uade.edu.ar", "1234567")))
                .isInstanceOf(DuplicateUserException.class);
        verify(emailVerification, never()).sendVerificationQuietly(any());
    }

    @Test
    void checksDuplicatesWithTheEmailInLowerCase() {
        service.register(request("JMartinez@UADE.edu.ar", "1234567"));

        verify(users).existsByEmail("jmartinez@uade.edu.ar");
    }

    @Test
    void rejectsAnEmailThatAlreadyHasAnAccount() {
        given(users.existsByEmail("jmartinez@uade.edu.ar")).willReturn(true);

        assertThatThrownBy(() -> service.register(request("jmartinez@uade.edu.ar", "1234567")))
                .isInstanceOfSatisfying(DuplicateUserException.class,
                        e -> assertThat(e.getFields()).containsExactly("email"));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void rejectsALegajoThatAlreadyHasAnAccount() {
        given(users.existsByLegajo("1234567")).willReturn(true);

        assertThatThrownBy(() -> service.register(request("jmartinez@uade.edu.ar", "1234567")))
                .isInstanceOfSatisfying(DuplicateUserException.class,
                        e -> assertThat(e.getFields()).containsExactly("legajo"));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void reportsBothFieldsWhenBothAreTaken() {
        given(users.existsByEmail("jmartinez@uade.edu.ar")).willReturn(true);
        given(users.existsByLegajo("1234567")).willReturn(true);

        assertThatThrownBy(() -> service.register(request("jmartinez@uade.edu.ar", "1234567")))
                .isInstanceOfSatisfying(DuplicateUserException.class,
                        e -> assertThat(e.getFields()).containsExactly("email", "legajo"));
    }

    @Test
    void doesNotSpendTimeHashingWhenTheAccountIsAlreadyTaken() {
        given(users.existsByLegajo("1234567")).willReturn(true);

        assertThatThrownBy(() -> service.register(request("jmartinez@uade.edu.ar", "1234567")))
                .isInstanceOf(DuplicateUserException.class);
        verify(encoder, never()).encode(any());
    }

    @Test
    void turnsARaceOnTheUniqueConstraintIntoTheSameError() {
        // El chequeo previo no vio nada, pero otro pedido se registró antes del INSERT: la base lo rechaza.
        given(users.existsByEmail("jmartinez@uade.edu.ar")).willReturn(false, true);
        given(users.saveAndFlush(any(User.class))).willThrow(new DataIntegrityViolationException("uk_app_user_email"));

        assertThatThrownBy(() -> service.register(request("jmartinez@uade.edu.ar", "1234567")))
                .isInstanceOfSatisfying(DuplicateUserException.class,
                        e -> assertThat(e.getFields()).containsExactly("email"));
    }

    @Test
    void letsAnIntegrityErrorThroughWhenNoDuplicateExplainsIt() {
        given(users.saveAndFlush(any(User.class))).willThrow(new DataIntegrityViolationException("otra cosa"));

        assertThatThrownBy(() -> service.register(request("jmartinez@uade.edu.ar", "1234567")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
