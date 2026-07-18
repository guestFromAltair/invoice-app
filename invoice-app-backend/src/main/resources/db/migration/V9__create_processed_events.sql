CREATE TABLE processed_events
(
    event_id     UUID PRIMARY KEY,
    consumer     VARCHAR(100)             NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);