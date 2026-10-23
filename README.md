# StockFlow

[![CI](https://github.com/bossshhh/stockflow/actions/workflows/ci.yml/badge.svg)](https://github.com/bossshhh/stockflow/actions/workflows/ci.yml)

An order and inventory management REST API for a small business: products, warehouse stock, customer
orders and fulfilment. It is built so that it **never sells stock it does not have**, even when many
customers order the last unit at the same moment.

**Java 21 · Spring Boot 3.5 · PostgreSQL 17 · Spring Security (JWT) · Flyway · Testcontainers · Docker · GitHub Actions**
**React 19 · TypeScript · Vite · TanStack Query · Tailwind CSS · shadcn/ui**

---

## Highlights

- **No overselling under concurrency.** Every stock change is a single atomic conditional `UPDATE`
  whose `WHERE` clause encodes the stock invariants; there is no read-check-write in Java. A test fires
  10 simultaneous orders at the last unit: exactly one wins.
- **Deadlock-free multi-item orders.** Rows are always locked in one global order (order row, then
  stock rows by ascending product id). Proven by a test that sends `[A, B]` and `[B, A]` orders from 20
  threads; removing the sorting makes it fail with `deadlock detected`.
- **Order lifecycle with stock side effects.** `PENDING → CONFIRMED → PACKED → SHIPPED → DELIVERED`:
  reserve on create, deduct on confirm, release or restock on cancel (confirmed orders only within a
  5-minute window, tested to the millisecond with an injectable clock; never once packed). Every status
  change is recorded with who and when, and drives the customer's tracking timeline.
- **Safe retries.** `POST /orders` accepts an `Idempotency-Key`: a retried request (lost response,
  double submit) returns the order already placed instead of placing it twice. The key is claimed with
  one `INSERT ... ON CONFLICT` before any stock is touched, so concurrent duplicates wait on the
  database and replay; 10 threads with one key create exactly one order.
- **Full audit trail.** Every stock change writes a movement row (type, delta, resulting levels,
  order id) in the same transaction.
- **Security.** Short-lived JWT access tokens plus rotating refresh tokens in an HttpOnly cookie with
  theft detection (a replayed token revokes the whole session). BCrypt, role-based
  access (customer / staff / admin) plus ownership checks: customers only ever see their own orders
  (and get 404, not 403, for others'). Password guessing is rate limited per account and per IP
  (only failed logins count, checked before BCrypt runs, 429 with `Retry-After`).
- **171 backend + 38 frontend tests, plus 16 browser tests** (unit, integration against real PostgreSQL,
  concurrency, security, end to end in Chromium). On every push GitHub Actions also starts the whole
  Docker stack from scratch and runs the browser tests and the API smoke test against it.

## Architecture

```
HTTP client ──► Controller (thin: validate, call one service method)
                    │
                    ▼
                Service (business rules, @Transactional, ownership checks)
                    │
                    ▼
                Repository (Spring Data JPA, atomic SQL) ──► PostgreSQL (schema owned by Flyway)
```

Package-by-feature, with dependencies pointing one way only:

```
com.stockflow
├── product/     catalog, unique SKUs
├── inventory/   stock levels, atomic stock changes, movement history, reports
├── order/       order lifecycle, search, reports
├── user/        accounts and roles
├── security/    Spring Security, JWT, login
├── common/      error handling, paging
└── config/
                 order → inventory → product        security → user
```

## Run it

Requirements: Docker. (To develop the UI: Node.js 24+. To run the backend outside Docker: JDK 21.)

```bash
cp .env.example .env          # set DB_PASSWORD, JWT_SECRET (openssl rand -base64 32), ADMIN_*
docker compose up -d --build  # PostgreSQL + API on :8080 + web UI on http://localhost:3000
scripts/seed-demo.sh          # optional: demo products, orders and accounts
```

Open **http://localhost:3000**. To work on the UI with hot reload instead:
`cd frontend && npm install && npm run dev` (http://localhost:5173).

Demo accounts after seeding: staff `staff@stockflow.local` / `staff-password-123`, warehouse
`warehouse@stockflow.local` / `warehouse-password-123`, customer
`ada@example.com` / `customer-password-123`, admin from `ADMIN_EMAIL` / `ADMIN_PASSWORD` in `.env`.

### Backend only

```bash
cp .env.example .env          # then set DB_PASSWORD, JWT_SECRET (openssl rand -base64 32), ADMIN_*
docker compose up -d --build  # PostgreSQL + API
```

- API: http://localhost:8080
- **Swagger UI: http://localhost:8080/swagger-ui.html** (log in via `POST /api/v1/auth/login`, then
  click **Authorize** and paste the `accessToken`)
- Health: http://localhost:8081/actuator/health

Development mode (app from the IDE or Maven, database in Docker):

```bash
docker compose up -d db
./mvnw spring-boot:run
```

Run all tests (Docker must be running, Testcontainers starts PostgreSQL):

```bash
./mvnw verify
```

End-to-end smoke test against the running system (81 checks over real HTTP, every role, including a
live race of 10 parallel orders for the last unit, 10 parallel retries with one idempotency key and the
login lockout):

```bash
scripts/smoke-test.sh            # defaults to http://localhost:8080
```

Browser end-to-end tests (Playwright, Chromium) against the running stack. They create their own
accounts and products, so they work on any database:

```bash
cd frontend
npx playwright install chromium  # once
npm run e2e                      # defaults to http://localhost:3000
```

## API overview

| Area | Endpoints | Who |
|---|---|---|
| Auth | `POST /api/v1/auth/register`, `/login`, `/refresh`, `/logout`, `GET /api/v1/auth/me` | public / signed in |
| Catalog | `GET /api/v1/catalog?q=`, `GET /api/v1/catalog/{id}` (active products, `inStock` flag, no counts) | public |
| Products | `GET/POST /api/v1/products`, `GET/PUT/DELETE /api/v1/products/{id}` | read: all; write: staff |
| Inventory | `GET /api/v1/inventory?q=` (name or SKU), `GET /{productId}`, `POST /{productId}/adjustments`, `GET /{productId}/movements`, `GET /low-stock`, `GET /valuation` | staff |
| Orders | `POST /api/v1/orders` (optional `Idempotency-Key`), `GET /api/v1/orders` (search), `GET /{id}` (with status history), `POST /{id}/confirm`, `POST /{id}/cancel`, `GET /summary` | customers: own orders; staff: all |
| Fulfilment | `POST /api/v1/orders/{id}/pack`, `/ship`, `/deliver` | warehouse and staff |
| Admin | `POST /api/v1/admin/users` | admin |

Every error has the same shape, with a stable machine-readable `code`:

```json
{ "timestamp": "...", "status": 409, "code": "INSUFFICIENT_STOCK",
  "message": "Not enough stock available for SKU PEAR", "path": "/api/v1/orders", "fieldErrors": [] }
```

## Engineering decisions (short version)

| Decision | Why |
|---|---|
| Conditional `UPDATE ... WHERE on_hand + :d >= reserved + :r` for every stock change | Check and change in one atomic statement; no race window |
| Database constraints back every invariant (`UNIQUE`, `CHECK reserved <= on_hand`) | Defence in depth; Java checks only produce nicer errors |
| `SELECT ... FOR UPDATE` on the order for confirm/cancel | Two simultaneous confirms are serialized; the second gets a clean 409 |
| Price, SKU and name snapshotted on order items | Later product edits never rewrite past orders |
| Testcontainers instead of H2 | H2 does not reproduce PostgreSQL locking; concurrency tests would prove nothing |
| Search returns summaries without items; reports aggregate in SQL | No N+1 queries, no loading thousands of rows into Java |
| JWT: 15-minute tokens, only `userId` + `role` inside, HS256 secret from the environment | Stateless auth; tokens are readable by anyone, so no personal data in them |
| Token buckets for failed logins (per email and per IP) and registrations (per IP); client IP from trusted proxies only | Stops password guessing and credential stuffing without slowing real users; a fake `X-Forwarded-For` cannot dodge it |
| `lock_timeout` (3 s) on every DB connection; lock conflicts → 409 `CONCURRENT_UPDATE` + `Retry-After` | One stuck transaction can no longer freeze the API; clients get a clean, retryable error |
| Flyway owns the schema, `ddl-auto: validate` | Versioned, reviewable schema changes; Hibernate never alters the database |

## Roadmap

- [x] Products, inventory, orders, search and reports
- [x] Security: JWT, roles, ownership
- [x] CI, Docker image, Docker Compose
- [x] Refresh tokens and public catalog for the UI
- [x] Web UI: shop, cart, order tracking, staff overview, orders, inventory, products, team
- [x] Fulfilment tracking: packed / shipped / delivered, status history, warehouse role
- [x] UI served by nginx in Docker Compose; Playwright browser tests in CI
- [ ] Carrier integration (DHL / UPS tracking numbers)
- [x] Idempotent order placement (`Idempotency-Key`)
- [x] Lock timeout with clean, retryable 409 errors
- [x] Login and registration rate limiting
- [ ] Hardening: token revocation, cleanup jobs
- [ ] Load test, observability, audit trail, multiple warehouses, deployment
