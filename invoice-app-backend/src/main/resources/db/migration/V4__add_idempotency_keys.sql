CREATE TABLE idempotency_keys
(
    id              UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(255)             NOT NULL,
    user_id         UUID                     NOT NULL REFERENCES users (id),
    request_path    VARCHAR(500)             NOT NULL,
    response_status INTEGER                  NOT NULL,
    response_body   TEXT                     NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now() + INTERVAL '24 hours',

    CONSTRAINT uq_idempotency_key_user_path UNIQUE (idempotency_key, user_id, request_path)
);

CREATE INDEX idx_idempotency_keys_expires_at ON idempotency_keys (expires_at);