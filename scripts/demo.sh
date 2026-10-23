#!/usr/bin/env bash
# Guided demo against a running instance. Core feature: needs nothing but curl.
# Usage: BASE_URL=http://localhost:8080 API_KEY=local-dev-key ./scripts/demo.sh   (SLOW=1 adds the timeout scenario)
set -u
BASE_URL="${BASE_URL:-http://localhost:8080}"
API_KEY="${API_KEY:-local-dev-key}"
RUN="${RUN_ID:-$(date +%s)}"

# Fail early and clearly if the API is not reachable (HTTP 000 = curl could not connect at all).
if ! curl -sS -o /dev/null --max-time 5 "$BASE_URL/actuator/health/readiness"; then
  printf '\n\033[1;31mNo hay API respondiendo en %s\033[0m\n' "$BASE_URL"
  echo "- Levanta la app en OTRA terminal: 'make run' (o 'make up') y espera a ver 'Started TransactionsApplication'."
  echo "- Ese comando deja la terminal ocupada: abre una segunda para 'make demo'."
  echo "- Si usas proxy/VPN, prueba: NO_PROXY=localhost,127.0.0.1 make demo"
  exit 1
fi

step() { printf '\n\033[1;36m== %s\033[0m\n' "$1"; }
say()  { printf '\033[2m%s\033[0m\n' "$1"; }

post() { # body [extra curl args...]
  local body="$1"; shift
  curl -sS -w '\n-> HTTP %{http_code}\n' -X POST "$BASE_URL/transactions" \
    -H 'Content-Type: application/json' -H "X-API-Key: $API_KEY" "$@" -d "$body"
}

ACC="acc-demo-$RUN"

step "1. CREDIT 1500.00 -> EXECUTED, balanceAfter 5500.00 (mock accounts start at 4000.00)"
post "{\"accountId\":\"$ACC\",\"type\":\"CREDIT\",\"amount\":1500.00,\"currency\":\"MXN\",\"description\":\"Transferencia recibida\"}" \
  -H "Idempotency-Key: demo-$RUN"

step "2. Same Idempotency-Key -> HTTP 200, same transaction, provider NOT called again"
post "{\"accountId\":\"$ACC\",\"type\":\"CREDIT\",\"amount\":1500.00,\"currency\":\"MXN\"}" -H "Idempotency-Key: demo-$RUN"

step "3. Same key, different amount -> HTTP 409 IDEMPOTENCY_KEY_CONFLICT"
post "{\"accountId\":\"$ACC\",\"type\":\"CREDIT\",\"amount\":9999.00,\"currency\":\"MXN\"}" -H "Idempotency-Key: demo-$RUN"

step "4. Provider rejection (business answer) -> 201 with status REJECTED"
post "{\"accountId\":\"acc-$RUN-nofunds\",\"type\":\"DEBIT\",\"amount\":100.00,\"currency\":\"MXN\"}"

step "5. Flaky provider: 2 failures then OK -> retry makes it EXECUTED"
say "(takes a second: exponential backoff between attempts)"
post "{\"accountId\":\"acc-$RUN-flaky\",\"type\":\"CREDIT\",\"amount\":100.00,\"currency\":\"MXN\"}"

step "6. Business rules -> HTTP 422, provider never called"
post "{\"accountId\":\"$ACC\",\"type\":\"DEBIT\",\"amount\":10000.01,\"currency\":\"MXN\"}"
post "{\"accountId\":\"$ACC\",\"type\":\"CREDIT\",\"amount\":1.00,\"currency\":\"MXN\"}"
post "{\"accountId\":\"$ACC\",\"type\":\"CREDIT\",\"amount\":100.00,\"currency\":\"USD\"}"

step "7. Provider down -> HTTP 502, transaction stored as FAILED (outcome unknown)"
post "{\"accountId\":\"acc-$RUN-down\",\"type\":\"CREDIT\",\"amount\":100.00,\"currency\":\"MXN\"}"
say "Repeat this call ~10 times: the circuit breaker opens and responses become immediate."

if [ "${SLOW:-0}" = "1" ]; then
  step "7b. Slow provider (5s) vs 2s read timeout -> FAILED after retries"
  post "{\"accountId\":\"acc-$RUN-slow\",\"type\":\"CREDIT\",\"amount\":100.00,\"currency\":\"MXN\"}"
fi

step "8. No API key -> HTTP 401"
curl -sS -w '\n-> HTTP %{http_code}\n' "$BASE_URL/transactions"

step "9. Query with filters and pagination"
curl -sS -w '\n-> HTTP %{http_code}\n' -H "X-API-Key: $API_KEY" "$BASE_URL/transactions?accountId=$ACC&limit=5&page=1"

printf '\nDone. Swagger UI: %s/swagger-ui.html\n' "$BASE_URL"
