# JMeter load test

Run headlessly:

```bash
jmeter -n -t load-test/flash-sale.jmx -l load-test/results.jtl \
  -Jusers=50 -Jloops=1 -JproductId=1 -Jquantity=1 -JduplicateRequests=false
```

Properties: `host` (localhost), `port` (8083), `users` (10), `rampSeconds` (1), `loops` (1), `productId` (1), `quantity` (1), and `duplicateRequests` (false). With duplicates enabled, every virtual user sends `duplicate-request`; otherwise IDs include the thread and iteration.

The plan uses a Java-23-compatible BeanShell preprocessor to create concrete request and user IDs. It intentionally has no Groovy/JSR223 dependency. With `duplicateRequests=false`, every iteration gets a UUID request ID. With `duplicateRequests=true`, every iteration uses the literal `duplicate-request`. The same concrete request ID is propagated as `X-Correlation-ID`.

Generate an HTML report with `jmeter -g load-test/results.jtl -o load-test/results`.
