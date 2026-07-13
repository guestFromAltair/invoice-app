CREATE TABLE outbox_events
(
    id             UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(50)              NOT NULL,
    aggregate_id   UUID                     NOT NULL,
    event_type     VARCHAR(100)             NOT NULL,
    payload        JSONB                    NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    published_at   TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
CREATE INDEX idx_outbox_aggregate ON outbox_events (aggregate_type, aggregate_id, created_at);