-- Prioridad vigente y auditoría de cambios de rol/prioridad (#121, ADR 0009).
-- Una fila por (usuario, categoría). Solo cuenta como prioridad si está APPROVED y no venció (expires_at NULL = sin
-- vencimiento); el vencimiento se evalúa al consultar, sin jobs. Los certificados de movilidad reducida (#123) usan
-- la misma tabla.

CREATE TABLE user_priority (
    id         UUID                     NOT NULL PRIMARY KEY,
    user_id    UUID                     NOT NULL,
    category   VARCHAR(20)              NOT NULL,
    status     VARCHAR(10)              NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    decided_at TIMESTAMP WITH TIME ZONE NOT NULL,
    decided_by UUID                     NOT NULL,
    CONSTRAINT uk_user_priority UNIQUE (user_id, category),
    CONSTRAINT fk_user_priority_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT ck_user_priority_category CHECK (category IN ('TEACHER', 'REDUCED_MOBILITY')),
    CONSTRAINT ck_user_priority_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

-- Quién cambió qué y cuándo. Solo ids y el nombre de la acción: sin emails ni datos de salud.
CREATE TABLE priority_audit (
    id             UUID                     NOT NULL PRIMARY KEY,
    actor_id       UUID                     NOT NULL,
    target_user_id UUID                     NOT NULL,
    action         VARCHAR(32)              NOT NULL,
    detail         VARCHAR(64),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_priority_audit_actor FOREIGN KEY (actor_id) REFERENCES app_user (id),
    CONSTRAINT fk_priority_audit_target FOREIGN KEY (target_user_id) REFERENCES app_user (id)
);

CREATE INDEX ix_priority_audit_target ON priority_audit (target_user_id);

-- Vigencia de la prioridad de docente. NULL = sin vencimiento hasta que el equipo lo defina.
INSERT INTO app_config (config_key, config_value, description) VALUES
    ('priority_teacher_validity', NULL, 'Vigencia de la prioridad de docente aprobada (a definir; NULL = no vence)');
