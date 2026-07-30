CREATE TABLE product (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    price NUMERIC(19,2) NOT NULL,
    available_quantity INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO product (id, name, price, available_quantity)
VALUES (1, 'Flash Sale Laptop', 1000.00, 100);
SELECT setval('product_id_seq', 1, true);
