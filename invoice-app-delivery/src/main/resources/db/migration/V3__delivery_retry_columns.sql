ALTER TABLE delivery_attempts ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX idx_delivery_retry ON delivery_attempts (next_attempt_at) WHERE status = 'FAILED';