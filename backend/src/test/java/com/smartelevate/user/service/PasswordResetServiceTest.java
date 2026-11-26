package com.smartelevate.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.smartelevate.common.mail.EmailSender;
import com.smartelevate.user.model.DeclaredUserType;
import com.smartelevate.user.model.TokenPurpose;
import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.RefreshTokenRepository;
import com.smartelevate.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T15:00:00Z");

    private final EmailTokenService tokens = mock(EmailTokenService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final EmailSender sender = mock(EmailSender.class);
    private final LoginThrottle loginThrottle = mock(LoginThrottle.class);
    private final PasswordResetService service = new PasswordResetService(tokens, users, refreshTokens, encoder,
            sender,
            new PasswordResetProperties(Duration.ofMinutes(30), Duration.ofMinutes(1)),
            new VerificationProperties(Duration.ofHours(24), Duration.ofMinutes(1), "https://app.test"),
            Clock.fixed(NOW, ZoneOffset.UTC), loginThrottle);

    private final User user = new User("Juana", "jmartinez@uade.edu.ar", "1234567", "viejo", DeclaredUserType.STUDENT, NOW);

    private void existingUser(boolean verified) {
        if (verified) {
            user.verifyEmail();
        }
        given(users.findByEmail("jmartinez@uade.edu.ar")).willReturn(Optional.of(user));
    }

    @Test
    void sendsALinkToVerifiedAccounts() {
        existingUser(true);
        given(tokens.lastIssuedAt(any(), eq(TokenPurpose.PASSWORD_RESET))).willReturn(Optional.empty());
        given(tokens.issue(any(), eq(TokenPurpose.PASSWORD_RESET), eq(Duration.ofMinutes(30)))).willReturn("tok123");

        service.request("  JMartinez@UADE.edu.ar ");

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(sender).send(eq("jmartinez@uade.edu.ar"), anyString(), body.capture());
        assertThat(body.getValue()).contains("https://app.test/recuperar/nueva?token=tok123");
    }

    @Test
    void unknownEmailDoesNothingAndDoesNotFail() {
        given(users.findByEmail(any())).willReturn(Optional.empty());

        assertThatCode(() -> service.request("nadie@uade.edu.ar")).doesNotThrowAnyException();
        verify(sender, never()).send(any(), any(), any());
        verify(tokens, never()).issue(any(), any(), any());
    }

    @Test
    void aSecondRequestInsideTheCooldownIsSilentlyIgnored() {
        existingUser(true);
        given(tokens.lastIssuedAt(any(), eq(TokenPurpose.PASSWORD_RESET)))
                .willReturn(Optional.of(NOW.minusSeconds(59)));

        assertThatCode(() -> service.request("jmartinez@uade.edu.ar")).doesNotThrowAnyException();
        verify(sender, never()).send(any(), any(), any());
    }

    @Test
    void aRequestAtExactlyTheCooldownGoesThrough() {
        existingUser(true);
        given(tokens.lastIssuedAt(any(), eq(TokenPurpose.PASSWORD_RESET)))
                .willReturn(Optional.of(NOW.minusSeconds(60)));
        given(tokens.issue(any(), any(), any())).willReturn("tok");

        service.request("jmartinez@uade.edu.ar");

        verify(sender).send(eq("jmartinez@uade.edu.ar"), anyString(), anyString());
    }

    @Test
    void unverifiedAccountsGetARealResetLink() {
        // #344: si otra persona registró este email, la dueña lo recupera eligiendo su clave.
        existingUser(false);
        given(tokens.lastIssuedAt(any(), eq(TokenPurpose.PASSWORD_RESET))).willReturn(Optional.empty());
        given(tokens.issue(any(), eq(TokenPurpose.PASSWORD_RESET), any())).willReturn("tok123");

        service.request("jmartinez@uade.edu.ar");

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(sender).send(eq("jmartinez@uade.edu.ar"), anyString(), body.capture());
        assertThat(body.getValue()).contains("/recuperar/nueva?token=tok123");
        verify(tokens, never()).issue(any(), eq(TokenPurpose.VERIFY_EMAIL), any());
    }

    @Test
    void mailFailuresDoNotLeakToTheCaller() {
        existingUser(true);
        given(tokens.lastIssuedAt(any(), any())).willReturn(Optional.empty());
        given(tokens.issue(any(), any(), any())).willReturn("tok");
        willThrow(new IllegalStateException("smtp caído")).given(sender).send(any(), any(), any());

        assertThatCode(() -> service.request("jmartinez@uade.edu.ar")).doesNotThrowAnyException();
    }

    @Test
    void resetStoresTheNewHashAndRevokesEverySession() {
        UUID id = UUID.randomUUID();
        given(tokens.consume("tok", TokenPurpose.PASSWORD_RESET)).willReturn(id);
        given(users.findById(id)).willReturn(Optional.of(user));
        given(encoder.encode("nueva-clave-1")).willReturn("hash-nuevo");

        service.reset("tok", "nueva-clave-1");

        assertThat(user.getPasswordHash()).isEqualTo("hash-nuevo");
        // Usar el link prueba que el email es suyo (#344).
        assertThat(user.isEmailVerified()).isTrue();
        verify(refreshTokens).revokeAllByUser(user.getId(), NOW);
        // Recuperó la cuenta por mail: el límite de intentos no la deja esperando.
        verify(loginThrottle).clearAccount("jmartinez@uade.edu.ar");
    }

    @Test
    void resetHashesTheNfkcFormOfThePassword() {
        UUID id = UUID.randomUUID();
        given(tokens.consume("tok", TokenPurpose.PASSWORD_RESET)).willReturn(id);
        given(users.findById(id)).willReturn(Optional.of(user));
        given(encoder.encode("contraseña-nueva")).willReturn("hash-nfkc");

        // "ñ" descompuesta: n + tilde combinable (U+0303).
        service.reset("tok", "contraseña-nueva");

        assertThat(user.getPasswordHash()).isEqualTo("hash-nfkc");
    }

    @Test
    void resetWithABadTokenChangesNothing() {
        given(tokens.consume("tok", TokenPurpose.PASSWORD_RESET))
                .willThrow(new InvalidTokenException(InvalidTokenException.Reason.EXPIRED));

        assertThatThrownBy(() -> service.reset("tok", "nueva-clave-1")).isInstanceOf(InvalidTokenException.class);
        assertThat(user.getPasswordHash()).isEqualTo("viejo");
        assertThat(user.isEmailVerified()).isFalse();
        verify(refreshTokens, never()).revokeAllByUser(any(), any());
        verify(loginThrottle, never()).clearAccount(any());
    }
}
