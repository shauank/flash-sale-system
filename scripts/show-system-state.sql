\echo '=== PRODUCT DATABASE ==='
\connect product_db
SELECT id, name, price, available_quantity, updated_at FROM product;

\echo '=== ORDER DATABASE ==='
\connect order_db
SELECT status, COUNT(*) FROM orders GROUP BY status ORDER BY status;
SELECT request_id, COUNT(*) FROM orders GROUP BY request_id HAVING COUNT(*) > 1;
SELECT * FROM orders WHERE status = 'PENDING' AND created_at < NOW() - INTERVAL '5 minutes';

\echo '=== RESERVATION DATABASE ==='
\connect reservation_db
SELECT status, COUNT(*) FROM reservation GROUP BY status ORDER BY status;
SELECT * FROM reservation WHERE status = 'RESERVED' AND created_at < NOW() - INTERVAL '5 minutes';

\echo '=== PAYMENT DATABASE ==='
\connect payment_db
SELECT status, COUNT(*) FROM payment GROUP BY status ORDER BY status;
SELECT * FROM payment WHERE status = 'SUCCESS';
