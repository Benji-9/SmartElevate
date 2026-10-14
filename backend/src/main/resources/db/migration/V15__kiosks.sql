-- Kioscos del QR con credencial de dispositivo (#229, ADR 0020). Cada kiosco está atado a un ascensor: el token del
-- QR toma el ascensor de acá, nunca de un parámetro del pedido. Del secreto se guarda solo el SHA-256 en hex, como
-- con refresh_token y email_token. Revocar = revoked_at; las filas no se borran (las referencia la auditoría).
CREATE TABLE kiosk (
    id            UUID                     NOT NULL PRIMARY KEY,
    elevator_code VARCHAR(8)               NOT NULL,
    secret_hash   VARCHAR(64)              NOT NULL,
    -- Kiosco del simulador de admin: sus check-ins cuentan como SIMULATED, no como reales.
    simulated     BOOLEAN                  NOT NULL,
    revoked_at    TIMESTAMP WITH TIME ZONE,
    last_seen_at  TIMESTAMP WITH TIME ZONE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_kiosk_secret_hash UNIQUE (secret_hash),
    CONSTRAINT fk_kiosk_elevator FOREIGN KEY (elevator_code) REFERENCES elevator (code)
);

-- Altas y revocaciones de kioscos van a la misma auditoría que roles y prioridad: el destino es un usuario o un
-- kiosco, nunca los dos ni ninguno.
ALTER TABLE priority_audit ALTER COLUMN target_user_id DROP NOT NULL;
ALTER TABLE priority_audit ADD COLUMN target_kiosk_id UUID;
ALTER TABLE priority_audit ADD CONSTRAINT fk_priority_audit_kiosk
    FOREIGN KEY (target_kiosk_id) REFERENCES kiosk (id);
ALTER TABLE priority_audit ADD CONSTRAINT ck_priority_audit_target
    CHECK ((target_user_id IS NULL) <> (target_kiosk_id IS NULL));

-- La rotación del QR es un parámetro técnico (TTL de un token firmado), no una regla de negocio: pasa a
-- application.yml (app.kiosk.qr-ttl, override APP_KIOSK_QR_TTL), decidido el 14/10 en #219. V1 y V3 no se editan.
DELETE FROM app_config WHERE config_key = 'qr_rotation';
