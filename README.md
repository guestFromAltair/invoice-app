# Invoice App

A production-grade invoicing and billing system built with Spring Boot and React.
Designed to demonstrate financial-domain Java patterns relevant to fintech and banking roles.

**[Live Demo](https://invoice-app-nine-alpha.vercel.app/)** ·
**[API Docs](https://invoice-app-production-d447.up.railway.app/swagger-ui.html)**

> Demo credentials: `demo@invoiceapp.com` / `password`

---

## What this demonstrates

This is not a todo app. The domain, invoicing and payment tracking
requires the same patterns used in real financial systems:

| Pattern             | Implementation                                                                     |
| ------------------- | ---------------------------------------------------------------------------------- |
| Idempotency         | Two-phase lock with DB constraint enforcement — prevents duplicate payments        |
| Audit trail         | Immutable append-only log of every financial event with old/new state snapshots   |
| Optimistic locking  | `@Version` on all entities — prevents lost updates under concurrent requests      |
| State machine       | Invoice lifecycle enforced in domain layer — illegal transitions rejected with 422 |
| Financial precision | `BigDecimal` everywhere, `NUMERIC(19,4)` in PostgreSQL — no floating point drift   |
| Reconciliation      | Scheduled job detects inconsistencies between invoice totals and payment records  |
| Observability       | Custom Micrometer metrics (outstanding balance gauge, invoice creation rate)       |
| Real-time           | Server-Sent Events push invoice status changes to connected clients instantly      |

---

## Tech stack

**Backend**
- Java 21, Spring Boot 3.3
- Spring Security with JWT authentication
- Spring Data JPA + Hibernate + PostgreSQL
- Flyway database migrations
- iText PDF generation
- Micrometer + Prometheus metrics

**Frontend**
- React 18 + TypeScript + Vite
- Redux Toolkit + RTK Query
- shadcn/ui + Tailwind CSS v4
- React Hook Form + Zod validation
- Server-Sent Events for real-time updates

**Infrastructure**
- Docker + docker-compose for local development
- Railway (backend + PostgreSQL)
- Vercel (frontend)
- Testcontainers for integration tests (real PostgreSQL, no H2)

---

## Architecture

```
invoice-app-frontend/     React SPA — deployed on Vercel
invoice-app-backend/      Spring Boot — deployed on Railway
  ├── auth/               JWT authentication, Spring Security
  ├── client/             Client management
  ├── invoice/            Invoice lifecycle, line items, payments, scheduler
  ├── pdf/                iText PDF generation
  ├── notification/       SSE real-time notifications
  └── shared/
      ├── audit/          Immutable audit logging
      ├── idempotency/    Duplicate request prevention
      ├── reconciliation/ Financial consistency checks
      └── metrics/        Custom Micrometer metrics
```

Package structure follows **package-by-feature** (domain isolation) rather than
package-by-layer. Each domain package contains its own controller, service, and
repository — clean boundaries that would allow extraction into separate services
if the team needed independent deployability.

---

## Invoice lifecycle

```
            ┌─→ PAID
            │
DRAFT ──→ SENT ───→ OVERDUE ──→ PAID
            │          │
            └──────────┴───→ CANCELLED
```

### Business Rules & Constraints
* **DRAFT**: Full mutable CRUD operations allowed on fields and line items. Transitions to `SENT` or `CANCELLED`.
* **SENT / OVERDUE**: Sealed from direct modification. Settled exclusively by recording an idempotent payment transaction or via an explicit `CANCELLED` action.
* **PAID / CANCELLED**: Final immutable terminal states. No further transitions or modifications allowed.
* **Data Retention**: To preserve historical financial integrity, a Client cannot be deleted if they have associated invoices.
* **Document Generation**: PDF generation is decoupled and available dynamically for any invoice state.

State transitions are enforced by the service layer — the status field
is never set directly. Illegal transitions (e.g. PAID → DRAFT) return
`422 Unprocessable Entity` with a descriptive error message.

---

## Key design decisions

**Why idempotency at the filter level?**
Payment idempotency could be implemented in the service layer, but doing it in a custom servlet filter means it intercepts
duplicate incoming payloads before hitting downstream business logic. By checking a distributed/DB lock using the
`Idempotency-Key` header, network retries are handled cleanly without risking dual-charges or state corruption.

**Why a scheduled reconciliation engine?**
In high-volume fintech platforms, distributed state anomalies happen. The scheduled reconciliation engine acts as a
secondary control loop. It runs asynchronously, calculating the mathematical sum of all valid payment transactions
against invoice balances to flag discrepancies, ensuring absolute system ledger consistency.

**Why granular Micrometer metrics?**
Standard HTTP metrics don't tell you the health of a business. This system exposes custom application metrics
(such as a Gauge tracking aggregate outstanding overdue balances and a Counter for invoice state transitions).
This allows engineering teams to construct Grafana dashboards capturing immediate business-level velocity and
operational risks.

**Why BigDecimal everywhere?**
`double` cannot represent 0.1 exactly in binary floating point. Across thousands of invoice calculations the
accumulated error becomes real money. `BigDecimal` is exact. All monetary values are stored as `NUMERIC(19,4)`
in PostgreSQL and handled as `BigDecimal` in Java.

**Why Testcontainers and not H2?**
H2 is not PostgreSQL. It has different SQL syntax, constraint behavior, and UUID handling. Tests that pass on H2
can fail in production. Testcontainers spins up a real PostgreSQL container, ensuring tests execute against the
exact database engine utilized in production.

---

## Running locally

**Prerequisites:** Docker, Java 21, Node.js 20+

```bash
# Clone
git clone https://github.com/guestFromAltair/invoice-app.git
cd invoice-app

# Start everything (PostgreSQL + Spring Boot backend)
docker compose up

# In a separate terminal — start the frontend
cd invoice-app-frontend
npm install
npm run dev
```

Open [http://localhost:5173](http://localhost:5173).

The backend API is at [http://localhost:8080](http://localhost:8080).
Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html).

---

## Running tests

```bash
# Backend — requires Docker (Testcontainers starts PostgreSQL automatically)
cd invoice-app-backend
./mvnw test

# Frontend
cd invoice-app-frontend
npm test
```

---

## API overview

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register new user |
| POST | `/api/auth/login` | Login, receive JWT |
| GET | `/api/clients` | List clients (paginated) |
| POST | `/api/clients` | Create client |
| GET | `/api/invoices` | List invoices (filtered, paginated) |
| POST | `/api/invoices` | Create invoice |
| GET | `/api/invoices/{id}` | Find invoice by id |
| PUT | `/api/invoices/{id}` | Update DRAFT invoice |
| POST | `/api/invoices/{id}/send` | Send invoice |
| POST | `/api/invoices/{id}/mark-paid` | Mark as paid |
| POST | `/api/invoices/{id}/cancel` | Cancel invoice |
| GET | `/api/invoices/{id}/pdf` | Download PDF |
| POST | `/api/invoices/{id}/payments` | Record payment (idempotent) |
| GET | `/api/audit/invoices/{id}` | Invoice audit trail |
| GET | `/api/admin/reconciliation/report` | Financial reconciliation report |
| GET | `/actuator/health` | Health check |

All protected endpoints require `Authorization: Bearer <token>` header.
Payment endpoints require `Idempotency-Key: <uuid>` header.

---

## What I would add next

- Multi-currency support with exchange rate snapshotting at invoice creation
- Recurring invoice scheduling
- Email reminders via SMTP when invoices become overdue
- Spring AI revenue assistant — natural language queries against invoice data
- Outbox pattern for reliable event publishing

---

*Built as a portfolio project targeting fintech and banking Java roles.*
*All financial patterns (idempotency, audit logging, reconciliation, optimistic locking)*
*are production-grade implementations, not tutorial-level demos.*