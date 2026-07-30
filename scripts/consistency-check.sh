#!/usr/bin/env bash
set -euo pipefail
docker compose exec -T postgres psql -U flashsale -d postgres < scripts/consistency-check.sql
