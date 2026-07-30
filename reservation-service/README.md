# Reservation Service

Owns reservation lifecycle data on port 8082. It accepts internal create/confirm/cancel calls and exposes public lookup. Expiry is recorded but intentionally not processed automatically.

Build with `mvn -pl reservation-service -am package`.
