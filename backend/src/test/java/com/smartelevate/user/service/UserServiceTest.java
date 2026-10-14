package com.smartelevate.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.smartelevate.priority.model.PriorityCategory;
import com.smartelevate.priority.service.PriorityService;
import com.smartelevate.user.dto.RegisterRequest;
import com.smartelevate.user.dto.UserResponse;
import com.smartelevate.user.model.DeclaredUserType;
import com.smartelevate.user.model.Role;
import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class UserServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T15:00:00Z");

    private final UserRepository users = mock(UserRepository.class);
    // Costo 4 (el mínimo de BCrypt): es el algoritmo real, pero los tests no pagan los ~140 ms de cada hash.
    private final PasswordEncoder encoder = spy(new BCryptPasswordEncoder(4));
    private final EmailVerificationService emailVerification = mock(EmailVerificationService.class);
    private final PriorityService priorityService = mock(PriorityService.class);
    private final UserService service = new UserService(users, encoder, Clock.fixed(NOW, ZoneOffset.UTC),
            emailVerification, priorityService);

    @Test
    void meReadsRoleFromTheDatabaseAndPriorityFromThePriorityService() {
        UUID id = UUID.randomUUID();
        User user = new User("Ana Pérez", "ana@uade.edu.ar", "1234567", "hash", DeclaredUserType.TEACHER, NOW);
        ReflectionTestUtils.setField(user, "id", id);
        user.assignRole(Role.REVIEWER);
        given(users.findById(id)).willReturn(Optional.of(user));
        given(priorityService.currentPriority(id)).willReturn(PriorityCategory.REDUCED_MOBILITY);

        assertThat(service.me(id)).isEqualTo(new UserResponse(id, "ana@uade.edu.ar", "Ana Pérez", "1234567",
                Role.REVIEWER, PriorityCategory.REDUCED_MOBILITY, DeclaredUserType.TEACHER));
    }

    @Test
    void meOfADeletedAccountIs401() {
        UUID id = UUID.randomUUID();
        given(users.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.me(id))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

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
