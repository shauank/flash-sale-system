#!/usr/bin/env bash
set -euo pipefail

quantity="${1:-100}"
curl --fail --silent --show-error -X POST "http://localhost:8081/internal/test/reset?quantity=${quantity}"
docker compose exec -T postgres psql -U flashsale -d reservation_db -c "TRUNCATE reservation RESTART IDENTITY;"
docker compose exec -T postgres psql -U flashsale -d payment_db -c "TRUNCATE payment RESTART IDENTITY;"
docker compose exec -T postgres psql -U flashsale -d order_db -c "TRUNCATE orders RESTART IDENTITY;"
echo
echo "Reset complete. Product 1 stock: ${quantity}"
