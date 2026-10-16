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
import com.smartelevate.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EmailVerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T15:00:00Z");
    private static final VerificationProperties PROPS =
            new VerificationProperties(Duration.ofHours(24), Duration.ofMinutes(1), "https://app.test");

    private final EmailTokenService tokens = mock(EmailTokenService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final EmailSender sender = mock(EmailSender.class);
    private final EmailVerificationService service =
            new EmailVerificationService(tokens, users, sender, PROPS, Clock.fixed(NOW, ZoneOffset.UTC));

    private final User user = new User("Juana", "jmartinez@uade.edu.ar", "1234567", "x", DeclaredUserType.STUDENT, NOW);

    @Test
    void sendsALinkWithTheTokenToTheUsersEmail() {
        given(tokens.issue(any(), eq(TokenPurpose.VERIFY_EMAIL), eq(Duration.ofHours(24)))).willReturn("tok123");

        service.sendVerification(user);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(sender).send(eq("jmartinez@uade.edu.ar"), anyString(), body.capture());
        assertThat(body.getValue()).contains("https://app.test/verificar-email?token=tok123");
    }

    @Test
    void quietVersionSwallowsMailFailures() {
        willThrow(new IllegalStateException("smtp caído")).given(sender).send(any(), any(), any());

        assertThatCode(() -> service.sendVerificationQuietly(user)).doesNotThrowAnyException();
    }

    @Test
    void verifyMarksTheAccountAsVerified() {
        UUID id = UUID.randomUUID();
        given(tokens.consume("tok", TokenPurpose.VERIFY_EMAIL)).willReturn(id);
        given(users.findById(id)).willReturn(Optional.of(user));

        service.verify("tok");

        assertThat(user.isEmailVerified()).isTrue();
    }

    @Test
    void verifyWithABadTokenLeavesTheAccountUnverified() {
        given(tokens.consume("tok", TokenPurpose.VERIFY_EMAIL))
                .willThrow(new InvalidTokenException(InvalidTokenException.Reason.EXPIRED));

        assertThatThrownBy(() -> service.verify("tok")).isInstanceOf(InvalidTokenException.class);
        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    void resendIsSilentForUnknownEmails() {
        given(users.findByEmail("nadie@uade.edu.ar")).willReturn(Optional.empty());

        service.resend("nadie@uade.edu.ar");

        verify(sender, never()).send(any(), any(), any());
    }

    @Test
    void resendIsSilentForAlreadyVerifiedAccounts() {
        user.verifyEmail();
        given(users.findByEmail("jmartinez@uade.edu.ar")).willReturn(Optional.of(user));

        service.resend("jmartinez@uade.edu.ar");

        verify(sender, never()).send(any(), any(), any());
    }

    @Test
    void resendIsRejectedInsideTheCooldownAndReportsTheRemainingWait() {
        given(users.findByEmail("jmartinez@uade.edu.ar")).willReturn(Optional.of(user));
        given(tokens.lastIssuedAt(any(), eq(TokenPurpose.VERIFY_EMAIL))).willReturn(Optional.of(NOW.minusSeconds(20)));

        assertThatThrownBy(() -> service.resend("jmartinez@uade.edu.ar"))
                .isInstanceOfSatisfying(ResendTooSoonException.class,
                        e -> assertThat(e.getRetryAfter()).isEqualTo(Duration.ofSeconds(40)));
        verify(sender, never()).send(any(), any(), any());
    }

    @Test
    void resendWorksOnceTheCooldownPassedAndNormalizesTheEmail() {
        given(users.findByEmail("jmartinez@uade.edu.ar")).willReturn(Optional.of(user));
        given(tokens.lastIssuedAt(any(), eq(TokenPurpose.VERIFY_EMAIL))).willReturn(Optional.of(NOW.minusSeconds(60)));
        given(tokens.issue(any(), any(), any())).willReturn("nuevo");

        service.resend(" JMartinez@UADE.edu.ar ");

        verify(sender).send(eq("jmartinez@uade.edu.ar"), anyString(), anyString());
    }
}
