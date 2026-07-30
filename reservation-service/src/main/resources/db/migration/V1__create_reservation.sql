CREATE TABLE reservation (
 id BIGSERIAL PRIMARY KEY,
 reservation_id VARCHAR(80) NOT NULL UNIQUE,
 order_id VARCHAR(80) NOT NULL,
 product_id BIGINT NOT NULL,
 user_id VARCHAR(120) NOT NULL,
 quantity INTEGER NOT NULL,
 status VARCHAR(20) NOT NULL,
 expires_at TIMESTAMPTZ NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_reservation_order_id ON reservation(order_id);
