# Deploying the live demo

One small Linux server runs everything with Docker Compose: PostgreSQL, the API and the UI **exactly as CI
published them** (pinned by digest), behind **Caddy**, which serves HTTPS with a free, automatically renewed
Let's Encrypt certificate. Only ports 80 and 443 are open.

```
Internet ──HTTPS──► Caddy ──► nginx (UI, /api proxy) ──► Spring Boot ──► PostgreSQL
           80/443     │                                       (none of these are published)
                      └── TLS certificate, HSTS, http→https redirect
```

## Files

| File | Purpose |
|---|---|
| `docker-compose.yml` | The four services; images come from `.env` as `image@sha256:...` |
| `Caddyfile` | HTTPS for `SITE_ADDRESS`, reverse proxy to the UI |
| `.env.example` | Settings and secrets template; the real `.env` lives only on the server |
| `deploy.sh <sha>` | Pulls the images CI published for that commit and pins them by digest |
| `reset-demo.sh` | Nightly: fresh database with demo data (run by cron) |
| `server-setup.sh` | One-time server setup: Docker, firewall, security updates, `deploy` user, SSH lock-down |

## First-time setup

1. **Server:** Ubuntu 24.04, **x86** (the images are built for amd64), 2 vCPU / 4 GB RAM is plenty.
   Add your SSH public key when creating it.
2. **Copy the files** (from the repo root, on your machine):
   ```bash
   scp deploy/server-setup.sh root@SERVER_IP:
   ssh root@SERVER_IP ./server-setup.sh
   ssh deploy@SERVER_IP true                      # must work before the next line
   ssh root@SERVER_IP ./server-setup.sh --lock-ssh
   rsync -av deploy/ scripts/seed-demo.sh deploy@SERVER_IP:/opt/stockflow/ --exclude README.md
   ```
3. **Settings** (on the server, in `/opt/stockflow`): `cp .env.example .env`, then fill in
   `SITE_ADDRESS` (e.g. `203-0-113-7.sslip.io`, the IP with dashes) and the secrets (`openssl rand ...`).
4. **Deploy** a commit whose CI run passed: `./deploy.sh <full commit sha>`, then `./seed-demo.sh "https://$SITE_ADDRESS"`.
5. **Nightly reset:** `crontab -e` and add
   `0 3 * * * cd /opt/stockflow && ./reset-demo.sh >> reset.log 2>&1`

## Day to day

- **Deploy a new version:** after CI is green on `main`, `./deploy.sh <full sha>` (the run summary also shows
  the digests). Roll back the same way with an older sha.
- **What is running:** `grep -E 'DEPLOYED_COMMIT|_IMAGE' .env` and `docker compose ps`.
- **Logs:** `docker compose logs -f app` (or `caddy`, `web`, `db`).
- **Check it end to end** (from your machine):
  `SMOKE_API_DOCS=off SMOKE_ALLOWED_ORIGIN=none ADMIN_EMAIL=... ADMIN_PASSWORD=... scripts/smoke-test.sh https://SITE_ADDRESS none`

## Security notes

- Secrets exist only in `/opt/stockflow/.env` on the server (readable by `deploy` only: `chmod 600 .env`).
- The demo accounts (customer, staff, warehouse) are public on purpose and the data resets nightly; the
  **admin** password is secret, because the admin can disable accounts.
- SSH: key only, no root login. Firewall: 22, 80, 443. Security updates install automatically.
- Docker bypasses the firewall for *published* ports, which is why only Caddy publishes any.
