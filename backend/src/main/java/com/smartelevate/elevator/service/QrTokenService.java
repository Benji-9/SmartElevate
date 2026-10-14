package com.smartelevate.elevator.service;

import com.smartelevate.elevator.dto.QrTokenResponse;
import com.smartelevate.elevator.model.Kiosk;
import com.smartelevate.elevator.repository.KioskRepository;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Token del QR del ascensor (ADR 0016, 0020): {@code base64url(elevatorId|kioskId|exp) + "." + base64url(HMAC)},
 * firmado con HMAC-SHA256 y la clave de {@code APP_KIOSK_QR_SECRET}. Corto para que el QR se lea de lejos y sin
 * estado en el servidor.
 *
 * <p>Los ciclos están alineados al reloj ({@code exp} es múltiplo del TTL): en un mismo ciclo un kiosco recibe
 * siempre el mismo token. {@link #validate} acepta el ciclo vigente y el inmediato anterior, para que no falle el
 * escaneo de un QR que rotó mientras la persona apuntaba la cámara.
 */
@Slf4j
@Service
public class QrTokenService {

    static final int MIN_SECRET_BYTES = 32;
    private static final String ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_DECODER = Base64.getUrlDecoder();

    private final SecretKeySpec key;
    private final long ttlSeconds;
    private final KioskRepository kiosks;
    private final Clock clock;

    public QrTokenService(KioskProperties props, KioskRepository kiosks, Clock clock) {
        this.key = new SecretKeySpec(secret(props.qrSecret()), ALGORITHM);
        this.ttlSeconds = props.qrTtl().toSeconds();
        this.kiosks = kiosks;
        this.clock = clock;
    }

    /** El token del ciclo vigente para el kiosco y cuándo vence (ahí el kiosco pide el siguiente). */
    public QrTokenResponse issue(Kiosk kiosk) {
        long exp = (clock.instant().getEpochSecond() / ttlSeconds + 1) * ttlSeconds;
        String payload = B64.encodeToString(
                (kiosk.getElevatorId() + "|" + kiosk.getId() + "|" + exp).getBytes(StandardCharsets.UTF_8));
        return new QrTokenResponse(payload + "." + B64.encodeToString(sign(payload)), Instant.ofEpochSecond(exp));
    }

    /**
     * Valida un token escaneado: firma, vencimiento (con tolerancia de un ciclo) y que el kiosco exista y no esté
     * revocado. Para el check-in (#230): el ascensor y si es simulado salen del token firmado y de la base, nunca
     * del pedido.
     *
     * @throws InvalidQrTokenException si el token no es válido por cualquier motivo
     */
    @Transactional(readOnly = true)
    public QrTokenClaims validate(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.", -1);
        if (parts.length != 2) {
            throw new InvalidQrTokenException();
        }
        byte[] signature;
        String[] fields;
        UUID kioskId;
        long exp;
        try {
            signature = B64_DECODER.decode(parts[1]);
            fields = new String(B64_DECODER.decode(parts[0]), StandardCharsets.UTF_8).split("\\|", -1);
            if (fields.length != 3) {
                throw new InvalidQrTokenException();
            }
            kioskId = UUID.fromString(fields[1]);
            exp = Long.parseLong(fields[2]);
        } catch (IllegalArgumentException e) { // Base64, UUID o número inválidos
            throw new InvalidQrTokenException();
        }
        // Tiempo constante: no revela cuántos bytes de la firma coinciden.
        if (!MessageDigest.isEqual(sign(parts[0]), signature)) {
            throw new InvalidQrTokenException();
        }
        if (clock.instant().getEpochSecond() >= exp + ttlSeconds) {
            throw new InvalidQrTokenException();
        }
        String elevatorId = fields[0];
        Kiosk kiosk = kiosks.findById(kioskId)
                .filter(k -> !k.isRevoked() && k.getElevatorId().equals(elevatorId))
                .orElseThrow(InvalidQrTokenException::new);
        return new QrTokenClaims(kiosk.getElevatorId(), kiosk.getId(), kiosk.isSimulated());
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e); // HmacSHA256 existe en toda JVM
        }
    }

    private static byte[] secret(String configured) {
        if (configured == null || configured.isBlank()) {
            // ponytail: clave efímera en dev/tests (en prod la exige KioskProdProperties); los QR no sobreviven a un
            // reinicio, pero rotan cada ~30 s igual.
            log.warn("APP_KIOSK_QR_SECRET no está definido: se usa una clave efímera (solo dev/tests)");
            byte[] bytes = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(bytes);
            return bytes;
        }
        byte[] bytes = configured.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("APP_KIOSK_QR_SECRET es demasiado corto: mínimo 32 bytes");
        }
        return bytes;
    }

    /** Lo que aporta un QR válido al check-in. */
    public record QrTokenClaims(String elevatorId, UUID kioskId, boolean simulated) {
    }
}
