-- Marcar un ascensor fuera de servicio o reactivarlo (#134) queda en la misma auditoría que roles, prioridad y
-- kioscos: el destino pasa a ser un usuario, un kiosco o un ascensor, exactamente uno de los tres.
ALTER TABLE priority_audit ADD COLUMN target_elevator_code VARCHAR(8);
ALTER TABLE priority_audit ADD CONSTRAINT fk_priority_audit_elevator
    FOREIGN KEY (target_elevator_code) REFERENCES elevator (code);
ALTER TABLE priority_audit DROP CONSTRAINT ck_priority_audit_target;
ALTER TABLE priority_audit ADD CONSTRAINT ck_priority_audit_target
    CHECK ((CASE WHEN target_user_id IS NULL THEN 0 ELSE 1 END
          + CASE WHEN target_kiosk_id IS NULL THEN 0 ELSE 1 END
          + CASE WHEN target_elevator_code IS NULL THEN 0 ELSE 1 END) = 1);
