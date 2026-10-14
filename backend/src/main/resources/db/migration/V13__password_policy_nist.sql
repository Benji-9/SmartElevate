-- Política de contraseñas alineada a NIST SP 800-63B (#211, ADR 0015).

-- 1) Argon2id. El hash con el prefijo de DelegatingPasswordEncoder ronda los 105 caracteres y no entra en 100.
ALTER TABLE app_user ALTER COLUMN password_hash SET DATA TYPE VARCHAR(255);

-- 2) Los hashes BCrypt de antes de #211 no tienen prefijo: se les agrega {bcrypt} para que sigan validando.
--    El login los re-hashea con Argon2id la próxima vez que la persona entra.
UPDATE app_user SET password_hash = '{bcrypt}' || password_hash WHERE password_hash LIKE '$2%';

-- 3) Logins fallidos, para el límite de intentos por cuenta y por IP. subject es el SHA-256 (hex) de
--    "account:<email>" o "ip:<dirección>": no se guardan emails ni IPs. Se borran al salir de la ventana.
CREATE TABLE login_failure (
    id        UUID                     NOT NULL PRIMARY KEY,
    subject   VARCHAR(64)              NOT NULL,
    failed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_login_failure_subject ON login_failure (subject, failed_at);

INSERT INTO app_config (config_key, config_value, description) VALUES
    ('login_failure_window',           'PT15M', 'Ventana deslizante del límite de logins fallidos'),
    ('login_max_failures_per_account', '10',    'Logins fallidos por cuenta dentro de la ventana (NIST: hasta 100)'),
    ('login_max_failures_per_ip',      '100',   'Logins fallidos por IP dentro de la ventana');
