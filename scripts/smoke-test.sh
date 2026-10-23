#!/usr/bin/env bash
# End-to-end smoke test against a RUNNING StockFlow (e.g. `docker compose up -d --build`).
# Plays every role over real HTTP: anonymous, customer, staff, admin. Creates its own uniquely named
# data, so it can be re-run against the same database.
#
# Usage:  scripts/smoke-test.sh [base-url] [management-url]
# Needs:  bash, curl, jq; ADMIN_EMAIL and ADMIN_PASSWORD in .env (or the environment).
set -uo pipefail

BASE="${1:-http://localhost:8080}"
MGMT="${2:-http://localhost:8081}"
API="$BASE/api/v1"
RUN="$(date +%s)"
PASSWORD="smoke-test-password-$RUN"

if [[ -f .env ]]; then set -a; . ./.env; set +a; fi
: "${ADMIN_EMAIL:?ADMIN_EMAIL must be set (in .env or the environment)}"
: "${ADMIN_PASSWORD:?ADMIN_PASSWORD must be set (in .env or the environment)}"

PASS=0
FAIL=0
STATUS=""
BODY=""

# request METHOD PATH [TOKEN] [JSON] -> sets STATUS and BODY
request() {
  local method="$1" path="$2" token="${3:-}" json="${4:-}"
  local args=(-s -o /tmp/stockflow-smoke-body -w "%{http_code}" -X "$method" "$path")
  [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
  [[ -n "$json" ]] && args+=(-H "Content-Type: application/json" -d "$json")
  STATUS="$(curl "${args[@]}")"
  BODY="$(cat /tmp/stockflow-smoke-body)"
}

check() {
  local description="$1" expected="$2" actual="$3"
  if [[ "$expected" == "$actual" ]]; then
    PASS=$((PASS + 1)); printf "  \033[32m✔\033[0m %s\n" "$description"
  else
    FAIL=$((FAIL + 1)); printf "  \033[31m✘ %s\033[0m (expected '%s', got '%s')\n" "$description" "$expected" "$actual"
    [[ -n "$BODY" ]] && printf "      response: %s\n" "$(echo "$BODY" | head -c 300)"
  fi
}

field() { echo "$BODY" | jq -r "$1"; }

login() {
  request POST "$API/auth/login" "" "{\"email\":\"$1\",\"password\":\"$2\"}"
  field .accessToken
}

stock() { # productId -> "onHand/reserved/available"
  request GET "$API/inventory/$1" "$STAFF"
  echo "$BODY" | jq -r '"\(.onHand)/\(.reserved)/\(.available)"'
}

echo "StockFlow smoke test against $BASE (run $RUN)"

echo; echo "1. Infrastructure"
check "health is UP (management port, no token)" "UP" "$(curl -s "$MGMT/actuator/health" | jq -r .status)"
check "Swagger UI is served" "200" "$(curl -s -o /dev/null -w "%{http_code}" -L "$BASE/swagger-ui.html")"
check "OpenAPI spec is served" "200" "$(curl -s -o /dev/null -w "%{http_code}" "$BASE/v3/api-docs")"

echo; echo "2. Authentication"
request GET "$API/products"
check "anonymous request -> 401" "401" "$STATUS"
check "401 uses ApiError body" "UNAUTHORIZED" "$(field .code)"
CUST_A_EMAIL="ada-$RUN@example.com"; CUST_B_EMAIL="bob-$RUN@example.com"
request POST "$API/auth/register" "" "{\"email\":\"$CUST_A_EMAIL\",\"password\":\"$PASSWORD\",\"fullName\":\"Ada $RUN\"}"
check "register customer A -> 201" "201" "$STATUS"
check "registered as CUSTOMER" "CUSTOMER" "$(field .role)"
check "response has no password hash" "null" "$(field .passwordHash)"
request POST "$API/auth/register" "" "{\"email\":\"$CUST_B_EMAIL\",\"password\":\"$PASSWORD\",\"fullName\":\"Bob $RUN\"}"
check "register customer B -> 201" "201" "$STATUS"
request POST "$API/auth/register" "" "{\"email\":\"$(echo "$CUST_A_EMAIL" | tr "[:lower:]" "[:upper:]")\",\"password\":\"$PASSWORD\",\"fullName\":\"Copy\"}"
check "same email in other case -> 409" "EMAIL_ALREADY_REGISTERED" "$(field .code)"
request POST "$API/auth/register" "" "{\"email\":\"weak-$RUN@example.com\",\"password\":\"short\",\"fullName\":\"Weak\"}"
check "weak password -> 400" "400" "$STATUS"
request POST "$API/auth/login" "" "{\"email\":\"$CUST_A_EMAIL\",\"password\":\"wrong-password-123\"}"
WRONG_PW_MSG="$(field .message)"
check "wrong password -> 401 INVALID_CREDENTIALS" "401 INVALID_CREDENTIALS" "$STATUS $(field .code)"
request POST "$API/auth/login" "" "{\"email\":\"nobody-$RUN@example.com\",\"password\":\"$PASSWORD\"}"
check "unknown email gives the identical message" "$WRONG_PW_MSG" "$(field .message)"
CUST_A="$(login "$CUST_A_EMAIL" "$PASSWORD")"
CUST_B="$(login "$CUST_B_EMAIL" "$PASSWORD")"
check "customer login returns a token" "true" "$([[ ${#CUST_A} -gt 50 ]] && echo true || echo false)"
request GET "$API/auth/me" "$CUST_A"
check "/me identifies customer A" "$CUST_A_EMAIL" "$(field .email)"
request GET "$API/auth/me" "not.a.valid-token"
check "garbage token -> 401" "401" "$STATUS"

echo; echo "3. Admin and staff accounts"
ADMIN="$(login "$ADMIN_EMAIL" "$ADMIN_PASSWORD")"
check "admin login works" "true" "$([[ ${#ADMIN} -gt 50 ]] && echo true || echo false)"
STAFF_EMAIL="staff-$RUN@stockflow.local"
request POST "$API/admin/users" "$ADMIN" "{\"email\":\"$STAFF_EMAIL\",\"password\":\"$PASSWORD\",\"fullName\":\"Staff $RUN\",\"role\":\"STAFF\"}"
check "admin creates staff -> 201" "201 STAFF" "$STATUS $(field .role)"
STAFF="$(login "$STAFF_EMAIL" "$PASSWORD")"
request POST "$API/admin/users" "$STAFF" "{\"email\":\"x-$RUN@stockflow.local\",\"password\":\"$PASSWORD\",\"fullName\":\"X\",\"role\":\"ADMIN\"}"
check "staff cannot create accounts -> 403" "403 FORBIDDEN" "$STATUS $(field .code)"

echo; echo "4. Products"
request POST "$API/products" "$STAFF" "{\"sku\":\"kb-$RUN\",\"name\":\"Keyboard\",\"price\":49.9}"
check "staff creates product -> 201" "201" "$STATUS"
check "SKU stored upper-case" "KB-$RUN" "$(field .sku)"
check "price normalized to 2 decimals" "49.90" "$(field .price)"
P1="$(field .id)"
request POST "$API/products" "$STAFF" "{\"sku\":\"KB-$RUN\",\"name\":\"Copy\",\"price\":1}"
check "duplicate SKU -> 409" "DUPLICATE_SKU" "$(field .code)"
request POST "$API/products" "$STAFF" "{\"sku\":\"a b\",\"name\":\"\",\"price\":-1}"
check "invalid product -> 400 with field errors" "400 3" "$STATUS $(field '.fieldErrors | length')"
request POST "$API/products" "$CUST_A" "{\"sku\":\"hack-$RUN\",\"name\":\"Hack\",\"price\":1}"
check "customer cannot create products -> 403" "403" "$STATUS"
request GET "$API/products?size=1" "$CUST_A"
check "customer can browse products" "200" "$STATUS"
request PUT "$API/products/$P1" "$STAFF" "{\"name\":\"Mechanical Keyboard\",\"price\":59.90}"
check "staff updates product" "200 59.90" "$STATUS $(field .price)"
request GET "$API/products?sort=secret" "$STAFF"
check "unknown sort field -> 400" "INVALID_PARAMETER" "$(field .code)"

echo; echo "5. Inventory"
check "new product starts at 0/0/0" "0/0/0" "$(stock "$P1")"
request POST "$API/inventory/$P1/adjustments" "$STAFF" '{"quantityChange":5,"reason":"Delivery"}'
check "receive 5 units" "5/0/5" "$(stock "$P1")"
request POST "$API/inventory/$P1/adjustments" "$STAFF" '{"quantityChange":-10,"reason":"Too many"}'
check "removing more than on hand -> 409" "INSUFFICIENT_STOCK" "$(field .code)"
request POST "$API/inventory/$P1/adjustments" "$STAFF" '{"quantityChange":0,"reason":"Nothing"}'
check "zero adjustment -> 400" "400" "$STATUS"
request GET "$API/inventory/$P1" "$CUST_A"
check "customer cannot see stock -> 403" "403" "$STATUS"

echo; echo "6. Order lifecycle"
request POST "$API/orders" "$CUST_A" "{\"items\":[{\"productId\":$P1,\"quantity\":1},{\"productId\":$P1,\"quantity\":1}]}"
check "customer A orders 2 (duplicates merged) -> 201 PENDING" "201 PENDING 1 2" "$STATUS $(field .status) $(field '.items | length') $(field '.items[0].quantity')"
check "name and email come from the account" "$CUST_A_EMAIL" "$(field .customerEmail)"
O1="$(field .id)"
check "stock reserved, not yet deducted" "5/2/3" "$(stock "$P1")"
request POST "$API/orders" "$CUST_A" "{\"items\":[{\"productId\":$P1,\"quantity\":4}]}"
check "ordering more than available -> 409" "INSUFFICIENT_STOCK" "$(field .code)"
check "customer error names the SKU without counts" "true" "$(echo "$BODY" | jq -r '.message | (contains("KB-") and (contains("on hand") | not))')"
request POST "$API/orders" "$STAFF" "{\"items\":[{\"productId\":$P1,\"quantity\":1}]}"
check "staff cannot place orders -> 403" "403" "$STATUS"
request POST "$API/orders/$O1/confirm" "$CUST_A"
check "customer cannot confirm -> 403" "403" "$STATUS"
request POST "$API/orders/$O1/confirm" "$STAFF"
check "staff confirms -> CONFIRMED" "200 CONFIRMED" "$STATUS $(field .status)"
check "stock deducted on confirm" "3/0/3" "$(stock "$P1")"
request POST "$API/orders/$O1/confirm" "$STAFF"
check "confirming twice -> 409" "INVALID_ORDER_STATE" "$(field .code)"
request GET "$API/orders/$O1" "$CUST_B"
check "customer B cannot see A's order -> 404" "404 ORDER_NOT_FOUND" "$STATUS $(field .code)"
request POST "$API/orders/$O1/cancel" "$CUST_B"
check "customer B cannot cancel A's order -> 404" "404" "$STATUS"
request POST "$API/orders/$O1/cancel" "$CUST_A"
check "A cancels confirmed order within 5 min -> CANCELLED" "200 CANCELLED" "$STATUS $(field .status)"
check "confirmed + cancelled -> restocked" "5/0/5" "$(stock "$P1")"
request POST "$API/orders" "$CUST_A" "{\"items\":[{\"productId\":$P1,\"quantity\":3}]}"
O2="$(field .id)"
request POST "$API/orders/$O2/cancel" "$CUST_A"
check "cancel pending order -> reservation released" "200 5/0/5" "$STATUS $(stock "$P1")"
request POST "$API/orders/$O2/cancel" "$CUST_A"
check "CANCELLED is terminal -> 409" "INVALID_ORDER_STATE" "$(field .code)"

echo; echo "7. Race for the last unit (10 parallel orders, real HTTP)"
request POST "$API/products" "$STAFF" "{\"sku\":\"last-$RUN\",\"name\":\"Last Unit\",\"price\":99}"
P2="$(field .id)"
request POST "$API/inventory/$P2/adjustments" "$STAFF" '{"quantityChange":1,"reason":"Only one"}'
RACE_DIR="$(mktemp -d)"
for i in $(seq 1 10); do
  curl -s -o /dev/null -w "%{http_code}\n" -X POST "$API/orders" -H "Authorization: Bearer $CUST_B" \
    -H "Content-Type: application/json" -d "{\"items\":[{\"productId\":$P2,\"quantity\":1}]}" > "$RACE_DIR/$i" &
done
wait
WINNERS="$(cat "$RACE_DIR"/* | grep -c '^201$')"
LOSERS="$(cat "$RACE_DIR"/* | grep -c '^409$')"
rm -rf "$RACE_DIR"
check "exactly 1 order won, 9 got 409" "1 9" "$WINNERS $LOSERS"
check "stock: 1 on hand, 1 reserved, 0 available" "1/1/0" "$(stock "$P2")"

echo; echo "7b. Safe retry: 10 parallel requests with ONE Idempotency-Key (real HTTP)"
request POST "$API/products" "$STAFF" "{\"sku\":\"idem-$RUN\",\"name\":\"Retry Lamp\",\"price\":20}"
P3="$(field .id)"
request POST "$API/inventory/$P3/adjustments" "$STAFF" '{"quantityChange":10,"reason":"Retry test"}'
IDEM_KEY="smoke-$RUN-checkout"
IDEM_DIR="$(mktemp -d)"
for i in $(seq 1 10); do
  curl -s -o "$IDEM_DIR/body-$i" -w "%{http_code}\n" -X POST "$API/orders" -H "Authorization: Bearer $CUST_B" \
    -H "Idempotency-Key: $IDEM_KEY" -H "Content-Type: application/json" \
    -d "{\"items\":[{\"productId\":$P3,\"quantity\":2}]}" > "$IDEM_DIR/status-$i" &
done
wait
CREATED="$(cat "$IDEM_DIR"/status-* | grep -c '^201$')"
REPLAYED="$(cat "$IDEM_DIR"/status-* | grep -c '^200$')"
DISTINCT_IDS="$(cat "$IDEM_DIR"/body-* | jq -r .id | sort -u | wc -l | tr -d ' ')"
rm -rf "$IDEM_DIR"
check "1 created, 9 replayed the same order" "1 9 1" "$CREATED $REPLAYED $DISTINCT_IDS"
check "stock reserved once: 10 on hand, 2 reserved" "10/2/8" "$(stock "$P3")"
STATUS="$(curl -s -o /tmp/stockflow-smoke-body -w "%{http_code}" -X POST "$API/orders" -H "Authorization: Bearer $CUST_B" \
  -H "Idempotency-Key: $IDEM_KEY" -H "Content-Type: application/json" -d "{\"items\":[{\"productId\":$P3,\"quantity\":5}]}")"
BODY="$(cat /tmp/stockflow-smoke-body)"
check "same key, different items -> 422" "422 IDEMPOTENCY_KEY_REUSED" "$STATUS $(field .code)"

echo; echo "8. Search, history and reports"
request GET "$API/orders?status=CANCELLED&customerEmail=$CUST_A_EMAIL" "$STAFF"
check "staff search: A's cancelled orders" "2" "$(field .totalElements)"
request GET "$API/orders?customerEmail=$CUST_A_EMAIL" "$CUST_B"
check "customer B searching for A's orders sees nothing" "0" "$(field .totalElements)"
request GET "$API/orders" "$CUST_A"
check "customer A sees only own orders" "true" "$(echo "$BODY" | jq --arg e "$CUST_A_EMAIL" '[.content[].customerEmail] | all(. == $e)')"
request GET "$API/orders?status=LOST" "$STAFF"
check "invalid status filter -> 400" "400" "$STATUS"
request GET "$API/inventory/$P1/movements?type=RESTOCK" "$STAFF"
check "movement history filtered by type" "RESTOCK $O1" "$(field '.content[0].type') $(field '.content[0].orderId')"
request GET "$API/inventory/low-stock?threshold=0&size=100" "$STAFF"
check "low-stock report includes the sold-out product" "true" "$(echo "$BODY" | jq --argjson id "$P2" '[.content[].productId] | index($id) != null')"
request GET "$API/inventory/valuation" "$STAFF"
check "valuation report responds" "200" "$STATUS"
FROM="$(date -u -v-1d +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -d '-1 day' +%Y-%m-%dT%H:%M:%SZ)"
TO="$(date -u -v+1d +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -d '+1 day' +%Y-%m-%dT%H:%M:%SZ)"
request GET "$API/orders/summary?from=$FROM&to=$TO" "$STAFF"
check "order summary report responds" "200" "$STATUS"
request GET "$API/orders/summary?from=$FROM&to=$TO" "$CUST_A"
check "customer cannot see reports -> 403" "403" "$STATUS"

echo; echo "9. Deactivated products"
request DELETE "$API/products/$P1" "$STAFF"
check "staff deactivates product -> 204" "204" "$STATUS"
request POST "$API/orders" "$CUST_A" "{\"items\":[{\"productId\":$P1,\"quantity\":1}]}"
check "deactivated product cannot be ordered" "PRODUCT_UNAVAILABLE" "$(field .code)"

echo; echo "10. CORS"
check "allowed origin gets CORS header" "http://localhost:5173" \
  "$(curl -s -o /dev/null -D - -X OPTIONS "$API/products" -H 'Origin: http://localhost:5173' \
     -H 'Access-Control-Request-Method: GET' | tr -d '\r' | awk -F': ' 'tolower($1)=="access-control-allow-origin"{print $2}')"
check "unknown origin is rejected" "403" \
  "$(curl -s -o /dev/null -w "%{http_code}" -X OPTIONS "$API/products" -H 'Origin: https://evil.example' \
     -H 'Access-Control-Request-Method: GET')"

echo; echo "11. Public catalog"
request GET "$API/catalog?q=last+unit&size=100"
check "anonymous can browse the catalog" "200" "$STATUS"
check "sold-out product shows inStock=false" "false" \
  "$(echo "$BODY" | jq -r --argjson id "$P2" '.content[] | select(.id == $id) | .inStock')"
check "catalog never shows stock counts" "null" "$(echo "$BODY" | jq -r '.content[0].onHand')"
request GET "$API/catalog/$P1"
check "deactivated product is hidden from the catalog" "404" "$STATUS"
check "/ redirects to Swagger" "302 /swagger-ui.html" \
  "$(curl -s -o /dev/null -w "%{http_code} %{redirect_url}" "$BASE/" | sed "s#$BASE##")"

echo; echo "12. Session: refresh cookie, rotation, logout"
JAR="$(mktemp)"
curl -s -o /dev/null -c "$JAR" -X POST "$API/auth/login" -H "Content-Type: application/json" \
  -d "{\"email\":\"$CUST_A_EMAIL\",\"password\":\"$PASSWORD\"}"
check "login stores an HttpOnly refresh cookie" "1" "$(grep -c '^#HttpOnly_.*stockflow_refresh' "$JAR")"
FIRST_COOKIE="$(awk '/stockflow_refresh/ {print $7}' "$JAR")"
check "refresh with the cookie -> new access token" "200" \
  "$(curl -s -o /dev/null -w "%{http_code}" -b "$JAR" -c "$JAR" -X POST "$API/auth/refresh")"
check "refresh rotated the cookie" "true" \
  "$([[ "$(awk '/stockflow_refresh/ {print $7}' "$JAR")" != "$FIRST_COOKIE" ]] && echo true || echo false)"
check "replaying the old cookie -> 401 (theft detected)" "401" \
  "$(curl -s -o /dev/null -w "%{http_code}" -b "stockflow_refresh=$FIRST_COOKIE" -X POST "$API/auth/refresh")"
check "...which also ended the legitimate session" "401" \
  "$(curl -s -o /dev/null -w "%{http_code}" -b "$JAR" -X POST "$API/auth/refresh")"
curl -s -o /dev/null -c "$JAR" -X POST "$API/auth/login" -H "Content-Type: application/json" \
  -d "{\"email\":\"$CUST_A_EMAIL\",\"password\":\"$PASSWORD\"}"
check "logout -> 204" "204" "$(curl -s -o /dev/null -w "%{http_code}" -b "$JAR" -X POST "$API/auth/logout")"
check "refresh after logout -> 401" "401" \
  "$(curl -s -o /dev/null -w "%{http_code}" -b "$JAR" -X POST "$API/auth/refresh")"
rm -f "$JAR"

echo
TOTAL=$((PASS + FAIL))
if [[ $FAIL -eq 0 ]]; then
  printf "\033[32mAll %d checks passed.\033[0m\n" "$TOTAL"
else
  printf "\033[31m%d of %d checks failed.\033[0m\n" "$FAIL" "$TOTAL"
fi
exit $(( FAIL > 0 ? 1 : 0 ))
