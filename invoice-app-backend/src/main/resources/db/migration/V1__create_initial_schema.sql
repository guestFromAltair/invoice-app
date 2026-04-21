CREATE TABLE users
(
    id         UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
    email      VARCHAR(255)             NOT NULL UNIQUE,
    password   VARCHAR(255)             NOT NULL,
    role       VARCHAR(50)              NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE clients
(
    id         UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
    owner_id   UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(255)             NOT NULL,
    email      VARCHAR(255),
    phone      VARCHAR(50),
    address    TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE invoices
(
    id              UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
    invoice_number  VARCHAR(50)              NOT NULL UNIQUE,
    client_id       UUID                     NOT NULL REFERENCES clients (id) ON DELETE RESTRICT,
    created_by      UUID                     NOT NULL REFERENCES users (id),
    status          VARCHAR(20)              NOT NULL DEFAULT 'DRAFT',
    issue_date      DATE                     NOT NULL DEFAULT CURRENT_DATE,
    due_date        DATE                     NOT NULL,
    subtotal        NUMERIC(19, 4)           NOT NULL DEFAULT 0,
    tax_rate        NUMERIC(5, 4)            NOT NULL DEFAULT 0,
    tax_amount      NUMERIC(19, 4)           NOT NULL DEFAULT 0,
    discount_amount NUMERIC(19, 4)           NOT NULL DEFAULT 0,
    total           NUMERIC(19, 4)           NOT NULL DEFAULT 0,
    notes           TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()

        CONSTRAINT chk_invoice_status
            CHECK (status IN ('DRAFT', 'SENT', 'PAID', 'OVERDUE', 'CANCELLED')),

    CONSTRAINT chk_invoice_total_positive
        CHECK (total >= 0)
);

CREATE TABLE line_items
(
    id           UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
    invoice_id   UUID                     NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    description  VARCHAR(500)             NOT NULL,
    quantity     NUMERIC(10, 2)           NOT NULL,
    unit_price   NUMERIC(19, 4)           NOT NULL,
    discount_pct NUMERIC(5, 4)            NOT NULL DEFAULT 0,
    line_total   NUMERIC(19, 4)           NOT NULL,
    position     INTEGER                  NOT NULL DEFAULT 0,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE payments
(
    id         UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
    invoice_id UUID                     NOT NULL REFERENCES invoices (id) ON DELETE RESTRICT,
    amount     NUMERIC(19, 4)           NOT NULL,
    paid_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    method     VARCHAR(50),
    notes      TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT chk_payment_amount_positive
        CHECK (amount > 0)
);

CREATE INDEX idx_clients_owner_id ON clients (owner_id);
CREATE INDEX idx_invoices_client_id ON invoices (client_id);
CREATE INDEX idx_invoices_status ON invoices (status);
CREATE INDEX idx_invoices_due_date ON invoices (due_date);
CREATE INDEX idx_line_items_invoice ON line_items (invoice_id);
CREATE INDEX idx_payments_invoice ON payments (invoice_id);