-- Tokens de un solo uso enviados por mail (#119 verificación de email; #138 reset de contraseña reutiliza la tabla).
-- Se guarda el hash SHA-256 (hex) del token, nunca el token: con la base filtrada no se puede armar el link.

CREATE TABLE email_token (
    id         UUID                     NOT NULL PRIMARY KEY,
    user_id    UUID                     NOT NULL,
    purpose    VARCHAR(32)              NOT NULL,
    token_hash VARCHAR(64)              NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    -- NULL = todavía sin usar.
    used_at    TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_email_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_email_token_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_email_token_purpose CHECK (purpose IN ('VERIFY_EMAIL', 'PASSWORD_RESET'))
);

CREATE INDEX ix_email_token_user_purpose ON email_token (user_id, purpose);
