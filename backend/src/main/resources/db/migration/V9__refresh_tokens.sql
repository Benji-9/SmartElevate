-- Refresh tokens de la sesión (#120, ADR 0009). Se guarda solo el hash SHA-256 del token: con la base
-- filtrada no se puede usar ninguno. Cada login abre una "familia"; cada refresh rota el token dentro de ella.
-- Si un token ya rotado (used_at) vuelve a presentarse, se asume robo y se revoca toda la familia.

CREATE TABLE refresh_token (
    id         UUID                     NOT NULL PRIMARY KEY,
    user_id    UUID                     NOT NULL,
    family_id  UUID                     NOT NULL,
    -- SHA-256 en hexadecimal (64 caracteres).
    token_hash VARCHAR(64)              NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Cuándo se canjeó por uno nuevo (rotación).
    used_at    TIMESTAMP WITH TIME ZONE,
    -- Logout o reuso detectado.
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);

CREATE INDEX ix_refresh_token_family ON refresh_token (family_id);
CREATE INDEX ix_refresh_token_user ON refresh_token (user_id);
