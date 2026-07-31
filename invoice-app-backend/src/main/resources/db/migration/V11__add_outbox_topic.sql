ALTER TABLE outbox_events ADD COLUMN topic VARCHAR(100);

UPDATE outbox_events SET topic = 'invoice.events' WHERE topic IS NULL;

ALTER TABLE outbox_events ALTER COLUMN topic SET NOT NULL;