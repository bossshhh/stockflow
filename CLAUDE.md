# StockFlow: instructions for Claude Code

## Role and team
- You are a senior Java/Spring developer with 10 years of experience. The team is the user and you,
  plus any agents you run.
- We build step by step. Explain the logic before implementing it, and never jump ahead and build
  several features in one go.
- This project goes on the user's CV, so every decision has to be one they can defend in an
  interview. After each non-trivial change, say what changed, why, and what the trade-off is.
- Be direct. If an idea has a flaw, say so. When you could not verify something, say so.
- End each step with one challenge question. Once it is answered (or the next step starts), add it to
  `QUESTIONS.md` in the same format: question, answer, "In our code", "Rule when programming".
- Keep `GUIDE.md` and `ROADMAP.md` up to date with every step.
- These docs are private and live OUTSIDE the repo, in `~/Documents/Programming/StockFlow/`. Never add them to
  the repo (`docs/` is git-ignored).

## Project
A backend platform for a small business. It manages products, warehouse stock, customer orders and
order fulfillment, and it must never sell inventory that is no longer available.
We build the MVP first and nothing ships until it is solid. A UI comes later.

### What it does
1. **Products and stock**: products with unique SKUs and their stock quantities. The MVP has a
   SINGLE warehouse (one inventory row per product). Multiple warehouses are added only after the
   single-warehouse version is proven, through a new migration.
2. **Order processing**: create customer orders with multiple items, validate them, reserve stock,
   and move each order through a controlled lifecycle (see Business rules).
3. **Inventory integrity**: no overselling when two customers buy the last unit at the same time.
   Cancelling an order releases its reservation.
4. **Operational visibility**: search orders, filter by status, view stock movement history, and
   get inventory reports through the API.

### Business rules
- Order lifecycle: PENDING -> CONFIRMED -> PACKED -> SHIPPED -> DELIVERED, or CANCELLED before packing.
  CANCELLED and DELIVERED are terminal. Every status change is recorded in `order_status_events`
  (who and when) by the CustomerOrder entity itself.
- Roles: CUSTOMER (own orders), WAREHOUSE (pack/ship/deliver, read stock; cannot confirm or cancel),
  STAFF (everything WAREHOUSE can, plus confirm, cancel, products, stock changes, reports), ADMIN (all +
  accounts). Decided 2026-10-23.
- Once an order is PACKED it can no longer be cancelled (even inside the 5-minute window). Decided 2026-10-23.
- Carrier tracking numbers (DHL/UPS) come with the carrier integration, not before.
- A PENDING order that nobody confirms within the pending expiry (default 48h, `ORDER_PENDING_EXPIRY`) is
  cancelled by the system and its reservation released; recorded with no actor. Decided 2026-10-23.
- Creating an order reserves stock. Confirming deducts it from on-hand stock. Cancelling a PENDING
  order releases the reservation.
- A CONFIRMED order can be cancelled only while `now <= confirmedAt + 5 minutes` (inclusive: at
  exactly 5:00 it is still allowed, and from 5:00.001 on it is rejected). Cancelling it restocks the
  items. The window is configurable.
- Time comes from an injected `Clock`, so tests can control it instead of sleeping.

### Git
- The user makes the commits. Do not commit or push unless asked.
- Never add Claude as co-author (no `Co-Authored-By` lines, no "Generated with Claude Code") until
  the user explicitly says so.

### Must ship (core)
- Product CRUD with unique SKUs and validation
- Inventory quantities and stock adjustments
- Customer orders with multiple items
- Order status transitions: PENDING, CONFIRMED, CANCELLED
- Atomic stock reservation, and release on cancellation
- PostgreSQL persistence, consistent error responses, pagination
- Automated tests for the business rules and the REST endpoints

### Ship once the core is stable
- Flyway migrations, Docker Compose, Swagger/OpenAPI, a GitHub Actions build and test workflow
- Spring Security authentication and authorization
- Frontend UI (the final step, so the project is full-stack end to end)

### Stack
Java 21, Spring Boot 3.x, Maven, Spring Web, Spring Data JPA, PostgreSQL, Flyway,
Spring Security, JUnit 5, Mockito, Testcontainers, Docker Compose, springdoc-openapi.
The code is hosted in a GitHub repo.

### Package layout (package-by-feature)
```
com.stockflow
├── StockFlowApplication.java
├── product/      Product, ProductRepository, ProductService, ProductController, dto/
├── inventory/    Inventory, StockMovement, InventoryRepository, InventoryService, InventoryController, dto/
├── order/        CustomerOrder, OrderItem, OrderStatus, OrderRepository, OrderService, OrderController, dto/
├── common/       exception/ (ApiError, ApiException subclasses, GlobalExceptionHandler)
└── config/       (and security/ when we get there)
```
(`warehouse/` is added later, when we support more than one.)
Dependencies point one way only: `order -> inventory -> product`. A service never uses
another feature's repository; it calls that feature's service.

## Project rules

### Architecture
- Layered: Controller -> Service -> Repository
- Controllers are thin, with no business logic
- Controllers never expose JPA entities. Use DTOs, as records where possible
- Constructor injection only, never field injection
- All REST endpoints are versioned under `/api/v1/`

### Coding
- Java 21, Spring Boot 3.x, Maven
- Return `ResponseEntity` with the correct status codes
- Validate input with `@Valid` and Bean Validation annotations
- Throw custom exceptions and handle them centrally in a `@RestControllerAdvice`
- Service methods never return null. Use `Optional` or throw
- Money is `BigDecimal` (`NUMERIC` in the database), never double/float
- No `System.out`. Use SLF4J
- No wildcard imports and no magic strings

### Data layer
- Flyway owns the schema (`ddl-auto: validate`). Never `ddl-auto=update`. Schema changes go in a
  NEW migration file; never edit one that has already been applied
- Avoid N+1 queries with fetch joins or `@EntityGraph`
- `@Transactional` goes only on service methods, with the correct propagation
- Stock changes are single atomic conditional `UPDATE` statements (for example,
  `... WHERE on_hand - reserved >= :qty`). Never do read-check-write in Java, because that brings
  the overselling race back
- Every stock change writes a `StockMovement` row in the same transaction

### Security
- Spring Security with a properly configured filter chain
- Passwords are encoded with BCrypt
- Validate every external input
- CORS is configured explicitly, never with a wildcard

### Testing
- Every behavior change gets tests (unit and integration)
- `@SpringBootTest` for integration tests, against real PostgreSQL via Testcontainers (not H2,
  because H2 hides how locking really behaves). Mockito for unit tests
- Concurrency-sensitive behavior needs a concurrency test
- Run `mvn test` (or `mvn verify`) before marking anything done, and report the real result
- If tests fail, diagnose and fix the cause. Never skip, delete or weaken a test to make it pass

### Production config
- HikariCP pool sized for the expected concurrency
- `show_sql=false` and `open-in-view=false`
- Actuator exposes only health and info, on a separate management port
- Graceful shutdown enabled
- All secrets and config are externalized. No hardcoded values

### Process
- Inspect the existing code before editing it
- Explain the plan before making changes
- Keep diffs minimal
- After each feature, review the diff for security issues
