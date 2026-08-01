CREATE TABLE delivery_attempts
(
    id             UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
    invoice_id     UUID                     NOT NULL,
    invoice_number VARCHAR(50)              NOT NULL,
    owner_id       UUID                     NOT NULL,
    recipient      VARCHAR(255)             NOT NULL,
    status         VARCHAR(30)              NOT NULL,
    attempts       INT                      NOT NULL DEFAULT 0,
    last_error     TEXT,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_delivery_invoice ON delivery_attempts (invoice_id);
CREATE INDEX idx_delivery_status  ON delivery_attempts (status);