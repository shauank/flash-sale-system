# Payment Service

Mock payment processor on port 8084. It deliberately sleeps on the request thread while holding its local transaction, making its small Tomcat and Hikari pools a visible bottleneck.

Control it with `PAYMENT_DELAY_MS` and `PAYMENT_FAILURE_PERCENTAGE`. Build with `mvn -pl payment-service -am package`.
