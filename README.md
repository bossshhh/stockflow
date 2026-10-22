# StockFlow

[![CI](https://github.com/bossshhh/stockflow/actions/workflows/ci.yml/badge.svg)](https://github.com/bossshhh/stockflow/actions/workflows/ci.yml)

An order and inventory management REST API for a small business: products, warehouse stock, customer
orders and fulfilment. It is built so that it **never sells stock it does not have**, even when many
customers order the last unit at the same moment.

**Java 21 · Spring Boot 3.5 · PostgreSQL 17 · Spring Security (JWT) · Flyway · Testcontainers · Docker · GitHub Actions**

---

## Highlights

- **No overselling under concurrency.** Every stock change is a single atomic conditional `UPDATE`
  whose `WHERE` clause encodes the stock invariants; there is no read-check-write in Java. A test fires
  10 simultaneous orders at the last unit: exactly one wins.
- **Deadlock-free multi-item orders.** Rows are always locked in one global order (order row, then
  stock rows by ascending product id). Proven by a test that sends `[A, B]` and `[B, A]` orders from 20
  threads; removing the sorting makes it fail with `deadlock detected`.
- **Order lifecycle with stock side effects.** `PENDING → CONFIRMED → CANCELLED`: reserve on create,
  deduct on confirm, release or restock on cancel (confirmed orders: within a 5-minute window, tested to
  the millisecond with an injectable clock).
- **Full audit trail.** Every stock change writes a movement row (type, delta, resulting levels,
  order id) in the same transaction.
- **Security.** JWT access tokens validated by Spring's OAuth2 Resource Server, BCrypt, role-based
  access (customer / staff / admin) plus ownership checks: customers only ever see their own orders
  (and get 404, not 403, for others').
- **108 automated tests** (unit, integration against real PostgreSQL, concurrency, security), run on
  every push by GitHub Actions.

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

Requirements: Docker. (For development without Docker for the app: JDK 21.)

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

End-to-end smoke test against the running system (64 checks over real HTTP, every role, including a
live race of 10 parallel orders for the last unit):

```bash
scripts/smoke-test.sh            # defaults to http://localhost:8080
```

## API overview

| Area | Endpoints | Who |
|---|---|---|
| Auth | `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `GET /api/v1/auth/me` | public / signed in |
| Products | `GET/POST /api/v1/products`, `GET/PUT/DELETE /api/v1/products/{id}` | read: all; write: staff |
| Inventory | `GET /api/v1/inventory`, `GET /{productId}`, `POST /{productId}/adjustments`, `GET /{productId}/movements`, `GET /low-stock`, `GET /valuation` | staff |
| Orders | `POST /api/v1/orders`, `GET /api/v1/orders` (search), `GET /{id}`, `POST /{id}/confirm`, `POST /{id}/cancel`, `GET /summary` | customers: own orders; staff: all |
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
| Flyway owns the schema, `ddl-auto: validate` | Versioned, reviewable schema changes; Hibernate never alters the database |

The full walkthrough of every file and decision is in [`docs/GUIDE.md`](docs/GUIDE.md). Design questions
with answers (concurrency, transactions, indexing, JWT) are in [`docs/QUESTIONS.md`](docs/QUESTIONS.md).

## Roadmap

- [x] Products, inventory, orders, search and reports
- [x] Security: JWT, roles, ownership
- [x] CI, Docker image, Docker Compose
- [ ] Hardening: idempotency keys, lock timeouts, login rate limiting, token revocation
- [ ] Load test with measured throughput
- [ ] Web UI
- [ ] Multiple warehouses
