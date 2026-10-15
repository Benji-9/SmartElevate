package com.smartelevate.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private static final String IP = "203.0.113.7";

    // Mismo esquema que PasswordEncoderConfig, con costos mínimos: algoritmos reales sin pagar el tiempo de prod.
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(4);
    private final PasswordEncoder encoder = new DelegatingPasswordEncoder("argon2",
            Map.of("argon2", new Argon2PasswordEncoder(16, 32, 1, 64, 1), "bcrypt", bcrypt));
    private final LoginThrottle throttle = mock(LoginThrottle.class);
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
                new JwtProperties("x", ACCESS_TTL, REFRESH_TTL), Clock.fixed(NOW, ZoneOffset.UTC), throttle);
        user = new User("Ana Pérez", "ana@uade.edu.ar", "1111111", encoder.encode("una-clave-larga"),
                DeclaredUserType.STUDENT, NOW);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.verifyEmail();
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
        TokenResponse response = service.login(" Ana@UADE.edu.ar ", "una-clave-larga", IP);

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);
        RefreshToken stored = savedToken();
        assertThat(stored.getUserId()).isEqualTo(user.getId());
        assertThat(stored.getTokenHash()).hasSize(64).isNotEqualTo(response.refreshToken());
        assertThat(stored.getExpiresAt()).isEqualTo(NOW.plus(REFRESH_TTL));
    }

    @Test
    void accessTokenCarriesOnlySubjectAndTimestamps_noRoleNorPriority() {
        TokenResponse response = service.login("ana@uade.edu.ar", "una-clave-larga", IP);

        Jwt jwt = decoder.decode(response.accessToken());
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(ACCESS_TTL));
        assertThat(jwt.getClaims().keySet()).containsExactlyInAnyOrder("sub", "iat", "exp");
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameError() {
        given(users.findByEmail("nadie@uade.edu.ar")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("ana@uade.edu.ar", "otra-clave-larga", IP))
                .isInstanceOf(InvalidCredentialsException.class).hasMessage("Email o contraseña incorrectos");
        assertThatThrownBy(() -> service.login("nadie@uade.edu.ar", "una-clave-larga", IP))
                .isInstanceOf(InvalidCredentialsException.class).hasMessage("Email o contraseña incorrectos");
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void unverifiedEmailCannotLogIn_butOnlyAfterTheCorrectPassword() {
        User unverified = new User("Ana Pérez", "ana@uade.edu.ar", "1111111", encoder.encode("una-clave-larga"),
                DeclaredUserType.STUDENT, NOW);
        given(users.findByEmail("ana@uade.edu.ar")).willReturn(Optional.of(unverified));

        assertThatThrownBy(() -> service.login("ana@uade.edu.ar", "una-clave-larga", IP))
                .isInstanceOf(EmailNotVerifiedException.class);
        // Con clave incorrecta no se revela que la cuenta existe.
        assertThatThrownBy(() -> service.login("ana@uade.edu.ar", "otra-clave-larga", IP))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void failuresAreRecordedForTheNormalizedAccountAndTheIp() {
        given(users.findByEmail("nadie@uade.edu.ar")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("ana@uade.edu.ar", "otra-clave-larga", IP))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> service.login(" Nadie@UADE.edu.ar ", "una-clave-larga", IP))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(throttle).recordFailure("ana@uade.edu.ar", IP);
        verify(throttle).recordFailure("nadie@uade.edu.ar", IP);
        verify(throttle, never()).clearAccount(any());
    }

    @Test
    void aSuccessfulLoginIsCheckedFirstAndThenClearsTheAccountFailures() {
        service.login("ana@uade.edu.ar", "una-clave-larga", IP);

        var order = inOrder(throttle, users);
        order.verify(throttle).check("ana@uade.edu.ar", IP);
        order.verify(users).findByEmail("ana@uade.edu.ar");
        order.verify(throttle).clearAccount("ana@uade.edu.ar");
        verify(throttle, never()).recordFailure(any(), any());
    }

    @Test
    void whenThrottledThePasswordIsNotEvenChecked() {
        willThrow(new TooManyLoginAttemptsException(Duration.ofSeconds(90))).given(throttle).check("ana@uade.edu.ar", IP);

        assertThatThrownBy(() -> service.login("ana@uade.edu.ar", "una-clave-larga", IP))
                .isInstanceOf(TooManyLoginAttemptsException.class);
        verify(users, never()).findByEmail(any());
        verify(throttle, never()).recordFailure(any(), any());
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void aLegacyBcryptHashIsUpgradedToArgon2idOnASuccessfulLogin() {
        // Así quedan los hashes de antes de #211 tras la migración V13: BCrypt con el prefijo {bcrypt}.
        user.changePasswordHash("{bcrypt}" + bcrypt.encode("una-clave-larga"));

        service.login("ana@uade.edu.ar", "una-clave-larga", IP);

        verify(users).save(user);
        assertThat(user.getPasswordHash()).startsWith("{argon2}$argon2id$");
        assertThat(encoder.matches("una-clave-larga", user.getPasswordHash())).isTrue();
    }

    @Test
    void anArgon2idHashIsNotRehashed() {
        String hash = user.getPasswordHash();

        service.login("ana@uade.edu.ar", "una-clave-larga", IP);

        assertThat(hash).startsWith("{argon2}");
        assertThat(user.getPasswordHash()).isEqualTo(hash);
        verify(users, never()).save(any());
    }

    @Test
    void aWrongPasswordDoesNotUpgradeTheLegacyHash() {
        String legacy = "{bcrypt}" + bcrypt.encode("una-clave-larga");
        user.changePasswordHash(legacy);

        assertThatThrownBy(() -> service.login("ana@uade.edu.ar", "otra-clave-larga", IP))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(user.getPasswordHash()).isEqualTo(legacy);
        verify(users, never()).save(any());
    }

    @Test
    void theUnknownEmailPathComparesAgainstAnArgon2idHash() {
        given(users.findByEmail("nadie@uade.edu.ar")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("nadie@uade.edu.ar", "una-clave-larga", IP))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat((String) ReflectionTestUtils.getField(service, "dummyHash")).startsWith("{argon2}$argon2id$");
    }

    @Test
    void theLoginNormalizesThePasswordWithNfkcLikeTheRegistration() {
        // Registrada con la "ñ" compuesta (U+00F1), escrita al entrar con n + tilde combinable (U+0303).
        user.changePasswordHash(encoder.encode("contraseña-larga"));

        assertThat(service.login("ana@uade.edu.ar", "contraseña-larga", IP).accessToken()).isNotBlank();
    }

    // ---- refresh

    private String lastRaw;

    private RefreshToken issueAndGetStored() {
        TokenResponse first = service.login("ana@uade.edu.ar", "una-clave-larga", IP);
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
