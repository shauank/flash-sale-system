CREATE TABLE payment (
 id BIGSERIAL PRIMARY KEY,
 payment_id VARCHAR(80) NOT NULL UNIQUE,
 order_id VARCHAR(80) NOT NULL,
 user_id VARCHAR(120) NOT NULL,
 amount NUMERIC(19,2) NOT NULL,
 status VARCHAR(20) NOT NULL,
 failure_reason VARCHAR(255),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_payment_order_id ON payment(order_id);
