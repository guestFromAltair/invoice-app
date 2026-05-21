CREATE TABLE audit_log
(
    id           UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
    entity_type  VARCHAR(50)              NOT NULL,
    entity_id    UUID                     NOT NULL,
    action       VARCHAR(50)              NOT NULL,
    old_value    JSONB,
    new_value    JSONB,
    performed_by UUID                     NOT NULL,
    performed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    ip_address   VARCHAR(45),
    request_id   VARCHAR(100)
);

CREATE INDEX idx_audit_log_entity ON audit_log (entity_type, entity_id, performed_at DESC);
CREATE INDEX idx_audit_log_user ON audit_log (performed_by, performed_at DESC);
CREATE INDEX idx_audit_log_performed_at ON audit_log (performed_at DESC);