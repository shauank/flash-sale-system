CREATE TABLE orders (
 id BIGSERIAL PRIMARY KEY,
 order_id VARCHAR(80) NOT NULL UNIQUE,
 request_id VARCHAR(120) NOT NULL,
 user_id VARCHAR(120) NOT NULL,
 product_id BIGINT NOT NULL,
 reservation_id VARCHAR(80),
 payment_id VARCHAR(80),
 quantity INTEGER NOT NULL,
 total_amount NUMERIC(19,2),
 status VARCHAR(20) NOT NULL,
 failure_reason VARCHAR(255),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_orders_request_id ON orders(request_id);
-- Deliberately no unique constraint on request_id.
