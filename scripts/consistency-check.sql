\set ON_ERROR_STOP on
CREATE EXTENSION IF NOT EXISTS dblink;

WITH orders AS (
  SELECT * FROM dblink('host=localhost dbname=order_db user=flashsale password=flashsale',
    'SELECT order_id,request_id,status,reservation_id,payment_id,created_at FROM orders')
    AS o(order_id text,request_id text,order_status text,reservation_id text,payment_id text,created_at timestamptz)
), payments AS (
  SELECT * FROM dblink('host=localhost dbname=payment_db user=flashsale password=flashsale','SELECT payment_id,order_id,status FROM payment')
    AS p(payment_id text,order_id text,payment_status text)
)
SELECT 'confirmed order without successful payment' AS problem,o.*
FROM orders o LEFT JOIN payments p ON p.payment_id=o.payment_id
WHERE o.order_status='CONFIRMED' AND COALESCE(p.payment_status,'')<>'SUCCESS'
UNION ALL
SELECT 'successful payment with non-confirmed order',o.*
FROM payments p LEFT JOIN orders o ON o.order_id=p.order_id
WHERE p.payment_status='SUCCESS' AND COALESCE(o.order_status,'')<>'CONFIRMED';

WITH orders AS (
  SELECT * FROM dblink('host=localhost dbname=order_db user=flashsale password=flashsale',
    'SELECT order_id,request_id,status,reservation_id,payment_id,created_at FROM orders')
    AS o(order_id text,request_id text,order_status text,reservation_id text,payment_id text,created_at timestamptz)
), reservations AS (
  SELECT * FROM dblink('host=localhost dbname=reservation_db user=flashsale password=flashsale','SELECT reservation_id,order_id,status,created_at FROM reservation')
    AS r(reservation_id text,order_id text,reservation_status text,created_at timestamptz)
)
SELECT 'confirmed order with non-confirmed reservation' AS problem,o.*
FROM orders o LEFT JOIN reservations r ON r.reservation_id=o.reservation_id
WHERE o.order_status='CONFIRMED' AND COALESCE(r.reservation_status,'')<>'CONFIRMED'
UNION ALL
SELECT 'failed order with confirmed reservation',o.* FROM orders o JOIN reservations r ON r.reservation_id=o.reservation_id
WHERE o.order_status='FAILED' AND r.reservation_status='CONFIRMED'
UNION ALL
SELECT 'cancelled reservation with confirmed order',o.* FROM orders o JOIN reservations r ON r.reservation_id=o.reservation_id
WHERE o.order_status='CONFIRMED' AND r.reservation_status='CANCELLED';

\connect order_db
\echo 'Orders without reservation or payment'
SELECT * FROM orders WHERE reservation_id IS NULL OR payment_id IS NULL;
\echo 'Duplicate request IDs'
SELECT request_id,COUNT(*) FROM orders GROUP BY request_id HAVING COUNT(*)>1;
\echo 'Long-running PENDING orders'
SELECT * FROM orders WHERE status='PENDING' AND created_at<NOW()-INTERVAL '5 minutes';
\connect reservation_db
\echo 'Long-running RESERVED reservations'
SELECT * FROM reservation WHERE status='RESERVED' AND created_at<NOW()-INTERVAL '5 minutes';
\connect postgres
\echo 'Payments without an order known to Order Service'
SELECT p.* FROM dblink('host=localhost dbname=payment_db user=flashsale password=flashsale','SELECT payment_id,order_id,status FROM payment') AS p(payment_id text,order_id text,payment_status text)
LEFT JOIN dblink('host=localhost dbname=order_db user=flashsale password=flashsale','SELECT order_id FROM orders') AS o(order_id text) ON o.order_id=p.order_id
WHERE o.order_id IS NULL;
