# Order Service

Public synchronous orchestrator on port 8083. It owns order records and talks to the other services only through `RestClient`.

The workflow intentionally has no distributed transaction, retry, circuit breaker, idempotency, or automatic recovery. Build with `mvn -pl order-service -am package`.
