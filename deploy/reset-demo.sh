#!/usr/bin/env bash
# Nightly (cron): wipes the demo database and seeds fresh demo data, so whatever visitors changed or broke
# is gone the next morning. Caddy keeps running, so the site is only briefly unavailable.
set -euo pipefail
cd "$(dirname "$0")"
set -a; . ./.env; set +a

docker compose stop app web
docker compose rm --stop --force db
docker volume rm stockflow-live_stockflow-db
docker compose up -d --wait --wait-timeout 300
./seed-demo.sh "https://$SITE_ADDRESS"
echo "$(date -u +%FT%TZ) demo reset done"
