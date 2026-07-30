# Product Service

Owns products and inventory on port 8081. Its read-check-write inventory update is deliberately unsafe under concurrency so lost updates and overselling can be observed.

Build with `mvn -pl product-service -am package`. Run with the root Docker Compose stack. With the `dev` profile enabled, reset inventory using `POST /internal/test/reset?quantity=100`.
