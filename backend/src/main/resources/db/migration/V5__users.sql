-- Cuentas de usuario (#118). Todo usuario nace común y sin verificar: el rol y la prioridad los asigna un ADMIN
-- (ADR 0009, docs/reglas/usuarios-y-prioridad.md). La tabla se llama app_user porque "user" es palabra
-- reservada en PostgreSQL y en H2.

CREATE TABLE app_user (
    id                 UUID                     NOT NULL PRIMARY KEY,
    full_name          VARCHAR(100)             NOT NULL,
    email              VARCHAR(254)             NOT NULL,
    legajo             VARCHAR(10)              NOT NULL,
    -- Hash BCrypt (60 caracteres), nunca la contraseña. Largo de sobra por si algún día cambia el algoritmo.
    password_hash      VARCHAR(100)             NOT NULL,
    role               VARCHAR(16)              NOT NULL DEFAULT 'USER',
    -- Lo que la persona dijo ser al registrarse. Es informativo: no otorga rol ni prioridad (#32).
    declared_user_type VARCHAR(16)              NOT NULL,
    -- La verificación por link llega con #119; hasta entonces ninguna cuenta queda verificada.
    email_verified     BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_app_user_email UNIQUE (email),
    CONSTRAINT uk_app_user_legajo UNIQUE (legajo),
    -- El email se guarda siempre en minúsculas: así Ana@uade.edu.ar y ana@uade.edu.ar no pueden convivir,
    -- aunque alguien inserte a mano saltándose la app.
    CONSTRAINT ck_app_user_email_lowercase CHECK (email = LOWER(email)),
    CONSTRAINT ck_app_user_role CHECK (role IN ('USER', 'REVIEWER', 'ADMIN')),
    CONSTRAINT ck_app_user_declared_user_type CHECK (declared_user_type IN ('STUDENT', 'TEACHER'))
);
