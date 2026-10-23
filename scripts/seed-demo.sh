#!/usr/bin/env bash
# Fills a running StockFlow with realistic demo data, THROUGH THE API (so every business rule applies):
# demo staff + customer accounts, products with stock, and a few orders in different states.
# Safe to re-run: existing accounts and SKUs are left alone.
#
# Usage:  scripts/seed-demo.sh [base-url]        (default http://localhost:8080)
# Needs:  bash, curl, jq; ADMIN_EMAIL and ADMIN_PASSWORD in .env (or the environment).
set -euo pipefail

BASE="${1:-http://localhost:8080}"
API="$BASE/api/v1"
JSON='Content-Type: application/json'

if [[ -f .env ]]; then set -a; . ./.env; set +a; fi
: "${ADMIN_EMAIL:?ADMIN_EMAIL must be set (in .env or the environment)}"
: "${ADMIN_PASSWORD:?ADMIN_PASSWORD must be set (in .env or the environment)}"

STAFF_EMAIL="staff@stockflow.local";   STAFF_PASSWORD="staff-password-123"
WAREHOUSE_EMAIL="warehouse@stockflow.local"; WAREHOUSE_PASSWORD="warehouse-password-123"
CUSTOMER_EMAIL="ada@example.com";      CUSTOMER_PASSWORD="customer-password-123"
CUSTOMER2_EMAIL="alan@example.com";    CUSTOMER2_PASSWORD="customer-password-123"

login() {
  local token
  token="$(curl -fsS -X POST "$API/auth/login" -H "$JSON" \
    -d "$(jq -n --arg e "$1" --arg p "$2" '{email: $e, password: $p}')" | jq -r .accessToken)"
  [[ -n "$token" && "$token" != "null" ]] || { echo "Login failed for $1" >&2; exit 1; }
  echo "$token"
}

# post PATH TOKEN BODY -> prints HTTP status; body saved in $OUT
OUT="$(mktemp)"; trap 'rm -f "$OUT"' EXIT
post() {
  local args=(-sS -o "$OUT" -w '%{http_code}' -X POST "$API$1" -H "$JSON" -d "$3")
  [[ -n "$2" ]] && args+=(-H "Authorization: Bearer $2")
  curl "${args[@]}"
}

# Product ids by SKU, without bash-4 associative arrays (macOS ships bash 3.2).
remember_id() { printf -v "ID_${1//-/_}" '%s' "$2"; }
id_of() { local var="ID_${1//-/_}"; echo "${!var}"; }

echo "Seeding $BASE"
ADMIN="$(login "$ADMIN_EMAIL" "$ADMIN_PASSWORD")"

status="$(post /admin/users "$ADMIN" "$(jq -n --arg e "$STAFF_EMAIL" --arg p "$STAFF_PASSWORD" \
  '{email: $e, password: $p, fullName: "Sam Staff", role: "STAFF"}')")"
echo "  staff account $STAFF_EMAIL: $([[ $status == 201 ]] && echo created || echo 'already exists')"
status="$(post /admin/users "$ADMIN" "$(jq -n --arg e "$WAREHOUSE_EMAIL" --arg p "$WAREHOUSE_PASSWORD" \
  '{email: $e, password: $p, fullName: "Wes Warehouse", role: "WAREHOUSE"}')")"
echo "  warehouse account $WAREHOUSE_EMAIL: $([[ $status == 201 ]] && echo created || echo 'already exists')"
for customer in "$CUSTOMER_EMAIL|Ada Lovelace" "$CUSTOMER2_EMAIL|Alan Turing"; do
  email="${customer%%|*}"; name="${customer##*|}"
  status="$(post /auth/register "" "$(jq -n --arg e "$email" --arg n "$name" --arg p "$CUSTOMER_PASSWORD" \
    '{email: $e, password: $p, fullName: $n}')")"
  echo "  customer account $email: $([[ $status == 201 ]] && echo created || echo 'already exists')"
done

STAFF="$(login "$STAFF_EMAIL" "$STAFF_PASSWORD")"
WAREHOUSE="$(login "$WAREHOUSE_EMAIL" "$WAREHOUSE_PASSWORD")"

# sku|name|description|price|stock
PRODUCTS=(
  "DSK-STAND-140|Standing desk 140 cm|Electric height adjustment, 70-120 cm|429.00|14"
  "CHR-ERGO-01|Ergonomic office chair|Mesh back, adjustable lumbar support|289.00|22"
  "LMP-ARM-02|Desk lamp with arm|Warm to cool white, USB-C powered|59.90|40"
  "MON-27-QHD|27-inch QHD monitor|2560 x 1440, 75 Hz, height-adjustable stand|249.00|9"
  "KBD-MECH-87|Mechanical keyboard 87-key|Brown switches, PBT keycaps|89.90|31"
  "MSE-VERT-01|Vertical ergonomic mouse|Wireless, 3 DPI levels|34.50|56"
  "HUB-USBC-7|USB-C hub 7-in-1|HDMI 4K, 100 W pass-through, SD reader|42.00|3"
  "CAM-1080-AF|1080p webcam with autofocus|Privacy shutter, dual microphones|64.00|2"
  "HDP-ANC-700|Noise-cancelling headphones|Over-ear, 30 h battery|179.00|0"
  "STD-LAPTOP-AL|Aluminium laptop stand|Six height positions, folds flat|29.90|75"
)

for entry in "${PRODUCTS[@]}"; do
  IFS='|' read -r sku name description price stock <<< "$entry"
  status="$(post /products "$STAFF" "$(jq -n --arg s "$sku" --arg n "$name" --arg d "$description" --argjson p "$price" \
    '{sku: $s, name: $n, description: $d, price: $p}')")"
  if [[ $status == 201 ]]; then
    id="$(jq -r .id "$OUT")"
    if (( stock > 0 )); then
      post "/inventory/$id/adjustments" "$STAFF" "{\"quantityChange\": $stock, \"reason\": \"Opening stock\"}" > /dev/null
    fi
    echo "  product $sku: created with $stock in stock"
  else
    id="$(curl -fsS "$API/catalog?q=$(jq -rn --arg n "$name" '$n | @uri')&size=5" | jq -r --arg s "$sku" '.content[] | select(.sku == $s) | .id')"
    echo "  product $sku: already exists"
  fi
  remember_id "$sku" "$id"
done

ADA="$(login "$CUSTOMER_EMAIL" "$CUSTOMER_PASSWORD")"
ALAN="$(login "$CUSTOMER2_EMAIL" "$CUSTOMER2_PASSWORD")"

order() { # token items-json -> order id ("" if refused, e.g. out of stock on a re-run)
  local status; status="$(post /orders "$1" "{\"items\": $2}")"
  [[ $status == 201 ]] && jq -r .id "$OUT" || echo ""
}

pending="$(order "$ADA" "[{\"productId\": $(id_of DSK-STAND-140), \"quantity\": 1}, {\"productId\": $(id_of CHR-ERGO-01), \"quantity\": 1}]")"
confirmed="$(order "$ALAN" "[{\"productId\": $(id_of MON-27-QHD), \"quantity\": 2}, {\"productId\": $(id_of KBD-MECH-87), \"quantity\": 2}]")"
cancelled="$(order "$ADA" "[{\"productId\": $(id_of LMP-ARM-02), \"quantity\": 3}]")"
second_pending="$(order "$ALAN" "[{\"productId\": $(id_of HUB-USBC-7), \"quantity\": 1}]")"
shipped="$(order "$ADA" "[{\"productId\": $(id_of MSE-VERT-01), \"quantity\": 2}]")"
delivered="$(order "$ALAN" "[{\"productId\": $(id_of STD-LAPTOP-AL), \"quantity\": 1}, {\"productId\": $(id_of LMP-ARM-02), \"quantity\": 1}]")"
[[ -n "$confirmed" ]] && post "/orders/$confirmed/confirm" "$STAFF" '{}' > /dev/null
[[ -n "$cancelled" ]] && post "/orders/$cancelled/cancel" "$ADA" '{}' > /dev/null
for id in $shipped $delivered; do
  post "/orders/$id/confirm" "$STAFF" '{}' > /dev/null
  post "/orders/$id/pack" "$WAREHOUSE" '{}' > /dev/null
  post "/orders/$id/ship" "$WAREHOUSE" '{}' > /dev/null
done
[[ -n "$delivered" ]] && post "/orders/$delivered/deliver" "$WAREHOUSE" '{}' > /dev/null
echo "  orders: pending ${pending:-skipped} and ${second_pending:-skipped}, confirmed ${confirmed:-skipped}," \
  "shipped ${shipped:-skipped}, delivered ${delivered:-skipped}, cancelled ${cancelled:-skipped}"

cat <<EOF

Done. Sign in at http://localhost:5173 with:
  staff     $STAFF_EMAIL / $STAFF_PASSWORD
  warehouse $WAREHOUSE_EMAIL / $WAREHOUSE_PASSWORD
  customer  $CUSTOMER_EMAIL / $CUSTOMER_PASSWORD   (also $CUSTOMER2_EMAIL)
  admin     \$ADMIN_EMAIL / \$ADMIN_PASSWORD from .env
EOF
