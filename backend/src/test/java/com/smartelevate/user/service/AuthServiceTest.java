package com.smartelevate.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.smartelevate.common.security.JwtProperties;
import com.smartelevate.user.dto.TokenResponse;
import com.smartelevate.user.model.DeclaredUserType;
import com.smartelevate.user.model.RefreshToken;
import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.RefreshTokenRepository;
import com.smartelevate.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T15:00:00Z");
    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TTL = Duration.ofDays(7);

    private final UserRepository users = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    // Costo 4 (mínimo de BCrypt): algoritmo real sin pagar ~140 ms por hash.
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final SecretKey key = new SecretKeySpec(
            "una-clave-de-prueba-de-32-bytes-o-mas!".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private final NimbusJwtDecoder decoder =
            NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    private AuthService service;
    private User user;

    @BeforeEach
    void setUp() {
        // El reloj de la app es fijo (NOW) y el real no: acá solo se inspecciona el contenido, sin validar el vencimiento.
        decoder.setJwtValidator(token -> OAuth2TokenValidatorResult.success());
        service = new AuthService(users, refreshTokens, encoder, new NimbusJwtEncoder(new ImmutableSecret<>(key)),
                new JwtProperties("x", ACCESS_TTL, REFRESH_TTL), Clock.fixed(NOW, ZoneOffset.UTC));
        user = new User("Ana Pérez", "ana@uade.edu.ar", "1111111", encoder.encode("una-clave-larga"),
                DeclaredUserType.STUDENT, NOW);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(user, "emailVerified", true);
        given(users.findByEmail("ana@uade.edu.ar")).willReturn(Optional.of(user));
    }

    private RefreshToken savedToken() {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).save(captor.capture());
        return captor.getValue();
    }

    // ---- login

    @Test
    void loginReturnsAccessAndRefreshAndStoresOnlyTheRefreshHash() {
        TokenResponse response = service.login(" Ana@UADE.edu.ar ", "una-clave-larga");

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);
        RefreshToken stored = savedToken();
        assertThat(stored.getUserId()).isEqualTo(user.getId());
        assertThat(stored.getTokenHash()).hasSize(64).isNotEqualTo(response.refreshToken());
        assertThat(stored.getExpiresAt()).isEqualTo(NOW.plus(REFRESH_TTL));
    }

    @Test
    void accessTokenCarriesOnlySubjectAndTimestamps_noRoleNorPriority() {
        TokenResponse response = service.login("ana@uade.edu.ar", "una-clave-larga");

        Jwt jwt = decoder.decode(response.accessToken());
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(ACCESS_TTL));
        assertThat(jwt.getClaims().keySet()).containsExactlyInAnyOrder("sub", "iat", "exp");
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameError() {
        given(users.findByEmail("nadie@uade.edu.ar")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("ana@uade.edu.ar", "otra-clave-larga"))
                .isInstanceOf(InvalidCredentialsException.class).hasMessage("Email o contraseña incorrectos");
        assertThatThrownBy(() -> service.login("nadie@uade.edu.ar", "una-clave-larga"))
                .isInstanceOf(InvalidCredentialsException.class).hasMessage("Email o contraseña incorrectos");
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void unverifiedEmailCannotLogIn_butOnlyAfterTheCorrectPassword() {
        ReflectionTestUtils.setField(user, "emailVerified", false);

        assertThatThrownBy(() -> service.login("ana@uade.edu.ar", "una-clave-larga"))
                .isInstanceOf(EmailNotVerifiedException.class);
        // Con clave incorrecta no se revela que la cuenta existe.
        assertThatThrownBy(() -> service.login("ana@uade.edu.ar", "otra-clave-larga"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // ---- refresh

    private String lastRaw;

    private RefreshToken issueAndGetStored() {
        TokenResponse first = service.login("ana@uade.edu.ar", "una-clave-larga");
        RefreshToken stored = savedToken();
        given(refreshTokens.findByTokenHash(stored.getTokenHash())).willReturn(Optional.of(stored));
        given(users.findById(user.getId())).willReturn(Optional.of(user));
        lastRaw = first.refreshToken();
        return stored;
    }

    @Test
    void refreshRotatesWithinTheSameFamily() {
        RefreshToken stored = issueAndGetStored();
        given(refreshTokens.markUsed(stored.getId(), NOW)).willReturn(1);

        TokenResponse next = service.refresh(lastRaw);

        assertThat(next.refreshToken()).isNotEqualTo(lastRaw);
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).getFamilyId()).isEqualTo(stored.getFamilyId());
    }

    @Test
    void reusingARotatedTokenRevokesTheWholeFamily() {
        RefreshToken stored = issueAndGetStored();
        ReflectionTestUtils.setField(stored, "usedAt", NOW.minusSeconds(30));

        assertThatThrownBy(() -> service.refresh(lastRaw)).isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokens).revokeFamily(stored.getFamilyId(), NOW);
        verify(refreshTokens, never()).markUsed(any(), any());
    }

    @Test
    void losingTheRaceToRedeemRevokesTheFamily() {
        RefreshToken stored = issueAndGetStored();
        given(refreshTokens.markUsed(stored.getId(), NOW)).willReturn(0);

        assertThatThrownBy(() -> service.refresh(lastRaw)).isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokens).revokeFamily(stored.getFamilyId(), NOW);
    }

    @Test
    void revokedAndExpiredAndUnknownTokensAreRejected() {
        RefreshToken stored = issueAndGetStored();
        ReflectionTestUtils.setField(stored, "expiresAt", NOW);
        assertThatThrownBy(() -> service.refresh(lastRaw)).isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokens, never()).markUsed(any(), any());

        ReflectionTestUtils.setField(stored, "expiresAt", NOW.plusSeconds(60));
        ReflectionTestUtils.setField(stored, "revokedAt", NOW.minusSeconds(1));
        assertThatThrownBy(() -> service.refresh(lastRaw)).isInstanceOf(InvalidRefreshTokenException.class);

        assertThatThrownBy(() -> service.refresh("desconocido")).isInstanceOf(InvalidRefreshTokenException.class);
    }

    // ---- logout

    @Test
    void logoutRevokesTheFamilyAndIsIdempotent() {
        RefreshToken stored = issueAndGetStored();

        service.logout(lastRaw);
        verify(refreshTokens).revokeFamily(eq(stored.getFamilyId()), eq(NOW));

        service.logout("desconocido"); // no explota
    }
}
