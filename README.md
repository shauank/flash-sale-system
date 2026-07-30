# Flash Sale System — deliberately imperfect baseline

This project is a hands-on system-design laboratory, not a production design. It starts with independently deployable microservices and intentionally preserves race conditions, runtime coupling, blocking calls, duplicate processing, and partial failure. Load it, observe it, form a hypothesis, change one design choice, and measure again.

## Repository

```text
flash-sale-system/
├── pom.xml                         Maven parent (convenience only)
├── docker-compose.yml              PostgreSQL + four services
├── .env.example                    tunable bottlenecks/failures
├── product-service/                port 8081, product_db
├── reservation-service/            port 8082, reservation_db
├── order-service/                  port 8083, order_db
├── payment-service/                port 8084, payment_db
├── load-test/
│   ├── flash-sale.jmx
│   └── README.md
└── scripts/
    ├── create-databases.sql
    ├── reset-test-data.sh
    ├── show-system-state.{sh,sql}
    └── consistency-check.{sh,sql}
```

Every service has its own `pom.xml`, Dockerfile, application, Flyway history, data model, API errors, correlation filter, metrics, tests, and README. The parent build does not turn them into one deployable application.

## Architecture and ownership

```text
Client
  |
  v
Order Service ──REST──> Product Service       product_db.product
     |  └──────REST──> Reservation Service   reservation_db.reservation
     └─────────REST──> Payment Service       payment_db.payment
          owns order_db.orders
```

| Service | Responsibility | Local transaction boundary |
|---|---|---|
| Product | Product lookup, unsafe stock reduce/restore | One product read/check/update |
| Reservation | Create, confirm, cancel, retrieve | One reservation operation |
| Order | Persist order steps and synchronously coordinate | Each order persistence step |
| Payment | Persist PENDING, block, choose result, persist result | One local payment transaction |

Identifiers connect records across databases; there are no cross-service database reads or foreign keys. `request_id` deliberately has a non-unique index.

## Synchronous workflow

1. `POST /api/orders` stores a new `PENDING` order.
2. Order gets the product price and calls Product to reduce stock.
3. Order calls Reservation to create a `RESERVED` record.
4. Order calls Payment. The payment request thread sleeps for the configured delay.
5. Success: confirm the reservation, then mark the order `CONFIRMED`.
6. Failure: try to cancel the reservation, try to restore stock, then mark the order `FAILED`.

This compensation is ordinary application code, not a Saga. Compensation calls can fail. A Payment timeout leaves the order `PENDING` and reservation `RESERVED`; Reservation unavailability after stock reduction deliberately does not restore stock. With forced after-payment failure, Payment is `SUCCESS` while Order remains `PENDING` and Reservation remains `RESERVED`.

## Build and run

Requirements: Java 21, Maven 3.9+, Docker with Compose v2, and optionally JMeter 5.6+.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS, if Maven selects an older JDK
mvn test
docker compose up --build
```

Wait until all health checks pass:

```bash
docker compose ps
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
```

Build one service independently with `mvn -pl product-service -am package` (replace the module name as needed). Stop with `docker compose down`. Use `docker compose down -v` only when you intentionally want to delete all database data.

The database creation script runs only when the PostgreSQL volume is first initialized. Flyway does not reset stock on restart.

## APIs

Create an order:

```bash
curl -i -X POST http://localhost:8083/api/orders \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-ID: demo-1' \
  -d '{"requestId":"req-10001","userId":"user-101","productId":1,"quantity":1}'
```

Inspect state:

```bash
curl http://localhost:8083/api/orders/ORDER_ID
curl http://localhost:8081/api/products/1
curl http://localhost:8081/api/products/1/inventory
curl http://localhost:8082/api/reservations/RESERVATION_ID
```

Product administration/internal APIs:

```bash
curl -X POST http://localhost:8081/api/products -H 'Content-Type: application/json' \
  -d '{"name":"Another Product","price":50,"availableQuantity":20}'
curl -X POST http://localhost:8081/internal/products/1/inventory/reduce -H 'Content-Type: application/json' \
  -d '{"orderId":"manual-order","requestId":"manual-request","quantity":1}'
curl -X POST http://localhost:8081/internal/products/1/inventory/restore -H 'Content-Type: application/json' \
  -d '{"orderId":"manual-order","requestId":"manual-request","quantity":1,"reason":"MANUAL"}'
```

Internal APIs are intentionally unauthenticated. Structured errors contain timestamp, HTTP status, error code, safe message, request ID, and service name.

## Configuration and failure injection

Copy `.env.example` to `.env` or set variables before `docker compose up`. Key controls:

| Variable | Default | Experiment |
|---|---:|---|
| `PAYMENT_DELAY_MS` | 500 | Blocking latency/thread saturation |
| `PAYMENT_FAILURE_PERCENTAGE` | 10 | Compensation behavior |
| `INVENTORY_DELAY_MS` / `INVENTORY_ERROR_PERCENTAGE` | 0 / 0 | Product bottleneck/failure |
| `RESERVATION_DELAY_MS` / `RESERVATION_ERROR_PERCENTAGE` | 0 / 0 | Partial workflow failure |
| `AFTER_PAYMENT_SUCCESS_PERCENTAGE` | 0 | Paid but incomplete order |
| `HTTP_CONNECT_TIMEOUT_MS` / `HTTP_READ_TIMEOUT_MS` | 1000 / 5000 | Client timeout behavior |
| `*_TOMCAT_THREADS` | varies | Thread-pool saturation |
| `*_HIKARI_POOL` | varies | Connection-pool saturation |

There is no retry, circuit breaker, bulkhead, load balancer, discovery, or fallback.

## Reset, inspect, and verify

Compose enables Product's `dev` reset endpoint. Reset owned data before a run:

```bash
./scripts/reset-test-data.sh 100
./scripts/show-system-state.sh
./scripts/consistency-check.sh
```

The consistency check uses PostgreSQL `dblink` from the administrative `postgres` database solely as a test/diagnostic script. Application services still cannot query other databases.

Useful direct queries:

```bash
docker compose exec postgres psql -U flashsale -d product_db -c \
  'SELECT id,name,available_quantity FROM product;'
docker compose exec postgres psql -U flashsale -d order_db -c \
  'SELECT status,COUNT(*) FROM orders GROUP BY status;'
docker compose exec postgres psql -U flashsale -d order_db -c \
  'SELECT request_id,COUNT(*) FROM orders GROUP BY request_id HAVING COUNT(*)>1;'
docker compose exec postgres psql -U flashsale -d reservation_db -c \
  'SELECT status,COUNT(*) FROM reservation GROUP BY status;'
docker compose exec postgres psql -U flashsale -d payment_db -c \
  'SELECT status,COUNT(*) FROM payment GROUP BY status;'
```

## Metrics and logs

Each service exposes `/actuator/health`, `/actuator/metrics`, and `/actuator/prometheus`. Search metrics for `flashsale_`, `tomcat_threads_*`, `hikaricp_connections_*`, and HTTP server metrics. Example:

```bash
curl -s http://localhost:8083/actuator/prometheus | grep flashsale
curl -s http://localhost:8084/actuator/prometheus | grep -E 'flashsale|tomcat_threads|hikaricp'
```

`X-Correlation-ID` is propagated to all service calls and placed in MDC. Order logs mark each orchestration step and include correlation/request/order context.

## JMeter and learning scenarios

See `load-test/README.md`. A typical race run:

```bash
./scripts/reset-test-data.sh 10
PAYMENT_FAILURE_PERCENTAGE=0 docker compose up -d
jmeter -n -t load-test/flash-sale.jmx -l load-test/results.jtl -Jusers=50 -Jloops=1
./scripts/show-system-state.sh
./scripts/consistency-check.sh
```

Run these experiments:

1. Basic: stock 100, 10 users, 500 ms payment.
2. Inventory race: stock 10, 50 users, no payment failure.
3. Payment bottleneck: stock 10,000, 100 users, payment delay 2,000 ms, 20 Tomcat threads, Hikari 5.
4. Order saturation: 300 users, Order threads 50, payment delay 3,000 ms.
5. Product bottleneck: 200 users, Product threads 10, inventory delay 500 ms.
6. Payment failure: 50 users, 30% failure; compare all four databases.
7. Duplicates: use `-JduplicateRequests=true`.
8. Stop Reservation during load; observe reduced stock and incomplete/missing reservation.
9. Stop Payment after reservations start; observe `PENDING`/`RESERVED`.
10. Set `AFTER_PAYMENT_SUCCESS_PERCENTAGE=100`; observe `SUCCESS` payment with incomplete order.

For a manual full-flow integration test, set payment failures to zero, start Compose, reset data, submit the curl request above, then run both state scripts. The order, reservation, and payment should be confirmed and stock should be 99.

## Expected baseline weaknesses

The baseline intentionally permits inventory races and lost updates; overselling or a misleading remaining count; duplicate orders, reservations, payments, and decrements; partial failures and failed compensation; no distributed transaction; reduced inventory without reservation; reserved inventory without payment completion; successful payment without order confirmation; synchronous latency amplification; thread-per-request blocking; Tomcat, HTTP, and Hikari saturation; cascading failure and tight runtime coupling; timeout ambiguity; no retry, circuit breaker, bulkhead, rate limit, back-pressure, reservation expiry worker, event recovery, outbox, DLQ, reconciliation, tracing, gateway, or discovery; manual configuration; and uneven scaling needs.

Some race outcomes vary because the point is to observe nondeterministic interleavings. A client timeout also does not prove the backend stopped processing.

## Scaling experiments

Change pool variables independently and compare throughput/latency. Stop one container while others remain healthy. Compose can create multiple instances with `docker compose up --scale payment-service=2`, but the fixed host port and Order's single service-name URL make this intentionally incomplete: remove fixed host-port mappings and add a reverse proxy/load balancer in a later phase. The baseline does not pretend that merely starting replicas provides a complete scaling solution.

## Improvement roadmap

1. **Baseline:** synchronous REST, separate databases, local transactions, manual compensation, load/failure testing.
2. **Correctness:** conditional atomic stock update; optimistic/pessimistic locking comparisons; idempotency and unique constraints; stronger transitions; expiry and reconciliation.
3. **Resilience:** measured timeouts; retry/backoff; circuit breaker; bulkhead; rate limiting; fallback decisions.
4. **Redis:** atomic Lua decrement, reservation TTL, idempotency, expiry restoration, Redis failure handling.
5. **Kafka:** async payment/workflow events, partitions, consumer groups, retry topics, DLQ, consumer idempotency.
6. **Consistency:** transactional outbox; Saga orchestration/choreography comparison; durable compensation/recovery.
7. **Observability:** centralized logs, Prometheus/Grafana, OpenTelemetry tracing, business consistency dashboards.
8. **Kubernetes:** deployments, independent scaling/HPA, probes, configuration/secrets, resources, disruption and rollout behavior.
9. **Advanced scaling:** gateway/discovery, database replicas and partitioning, multi-region design, failure isolation.

Do not jump to a later phase until a baseline failure has been reproduced and measured.
