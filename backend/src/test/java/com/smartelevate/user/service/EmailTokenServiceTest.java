package com.smartelevate.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.smartelevate.user.model.EmailToken;
import com.smartelevate.user.model.TokenPurpose;
import com.smartelevate.user.repository.EmailTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EmailTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T15:00:00Z");

    private final EmailTokenRepository repo = mock(EmailTokenRepository.class);
    private final EmailTokenService service = new EmailTokenService(repo, Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID userId = UUID.randomUUID();

    @Test
    void issuesARandomTokenAndStoresOnlyItsHashWithTheExpiry() {
        String a = service.issue(userId, TokenPurpose.VERIFY_EMAIL, Duration.ofHours(24));
        String b = service.issue(userId, TokenPurpose.VERIFY_EMAIL, Duration.ofHours(24));

        assertThat(a).isNotEqualTo(b).hasSizeGreaterThanOrEqualTo(43);
        ArgumentCaptor<EmailToken> saved = ArgumentCaptor.forClass(EmailToken.class);
        verify(repo, org.mockito.Mockito.times(2)).save(saved.capture());
        EmailToken first = saved.getAllValues().get(0);
        assertThat(first.getTokenHash()).hasSize(64).isNotEqualTo(a).doesNotContain(a);
        assertThat(first.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(first.getPurpose()).isEqualTo(TokenPurpose.VERIFY_EMAIL);
    }

    @Test
    void issuingInvalidatesThePendingTokensOfTheSameUserAndPurpose() {
        service.issue(userId, TokenPurpose.PASSWORD_RESET, Duration.ofHours(1));

        verify(repo).deleteByUserIdAndPurposeAndUsedAtIsNull(userId, TokenPurpose.PASSWORD_RESET);
    }

    @Test
    void consumesAValidTokenAndReturnsTheUser() {
        given(repo.markUsed(anyString(), eq(TokenPurpose.VERIFY_EMAIL), eq(NOW))).willReturn(1);
        given(repo.findByTokenHash(anyString())).willReturn(Optional.of(stored(null, NOW.plusSeconds(10))));

        assertThat(service.consume("tok", TokenPurpose.VERIFY_EMAIL)).isEqualTo(userId);
    }

    @Test
    void anUnknownTokenIsInvalid() {
        given(repo.findByTokenHash(anyString())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.consume("tok", TokenPurpose.VERIFY_EMAIL))
                .isInstanceOfSatisfying(InvalidTokenException.class,
                        e -> assertThat(e.getReason()).isEqualTo(InvalidTokenException.Reason.INVALID));
    }

    @Test
    void aTokenOfAnotherPurposeIsInvalid() {
        given(repo.findByTokenHash(anyString())).willReturn(Optional.of(stored(null, NOW.plusSeconds(10))));

        assertThatThrownBy(() -> service.consume("tok", TokenPurpose.PASSWORD_RESET))
                .isInstanceOfSatisfying(InvalidTokenException.class,
                        e -> assertThat(e.getReason()).isEqualTo(InvalidTokenException.Reason.INVALID));
    }

    @Test
    void anAlreadyUsedTokenIsReportedAsUsed() {
        given(repo.markUsed(anyString(), any(), any())).willReturn(0);
        given(repo.findByTokenHash(anyString())).willReturn(Optional.of(stored(NOW.minusSeconds(5), NOW.plusSeconds(10))));

        assertThatThrownBy(() -> service.consume("tok", TokenPurpose.VERIFY_EMAIL))
                .isInstanceOfSatisfying(InvalidTokenException.class,
                        e -> assertThat(e.getReason()).isEqualTo(InvalidTokenException.Reason.USED));
    }

    @Test
    void anExpiredTokenIsReportedAsExpired() {
        given(repo.markUsed(anyString(), any(), any())).willReturn(0);
        given(repo.findByTokenHash(anyString())).willReturn(Optional.of(stored(null, NOW.minusSeconds(1))));

        assertThatThrownBy(() -> service.consume("tok", TokenPurpose.VERIFY_EMAIL))
                .isInstanceOfSatisfying(InvalidTokenException.class,
                        e -> assertThat(e.getReason()).isEqualTo(InvalidTokenException.Reason.EXPIRED));
    }

    private EmailToken stored(Instant usedAt, Instant expiresAt) {
        EmailToken token = new EmailToken(userId, TokenPurpose.VERIFY_EMAIL, "h", NOW.minusSeconds(100), expiresAt);
        org.springframework.test.util.ReflectionTestUtils.setField(token, "usedAt", usedAt);
        return token;
    }
}
