# 0001 — Quarkus + Vaadin Banking App MVP

## Overview

A single Maven project combining a Quarkus backend and Vaadin Flow frontend, with
Hibernate ORM + Panache against PostgreSQL. No authentication, no multi-module setup.
The Vaadin UI is server-side Java; the Quarkus CDI container manages all beans
including Vaadin views.

Versions (Feb 2026): Quarkus 3.20 LTS, Vaadin 24.9.x LTS, Java 21.

---

## Steps

### 1. Project Skeleton

Create a standard Maven project at the workspace root with `pom.xml` targeting Java 21
and `<packaging>jar</packaging>`. Set version properties for `quarkus.version` (3.20+)
and `vaadin.version` (24.9.x).

Configure `<dependencyManagement>` with **Vaadin BOM declared first**, then Quarkus BOM —
this ordering prevents JNA version conflicts at runtime.

Add the `quarkus-maven-plugin` as the sole build plugin. No separate Vaadin plugin is
needed; the Vaadin frontend build is embedded inside the extension.

### 2. Dependencies

Add to `<dependencies>`:

| Artifact | Purpose |
|---|---|
| `com.vaadin:vaadin-quarkus-extension` | Vaadin/Quarkus CDI bridge |
| `com.vaadin:vaadin-jandex` | Jandex index for Vaadin components |
| `io.quarkus:quarkus-hibernate-orm-panache` | ORM + Panache repository support |
| `io.quarkus:quarkus-jdbc-postgresql` | JDBC driver |
| `io.quarkus:quarkus-flyway` | Database migration management |
| `io.quarkus:quarkus-arc` | CDI container (declare explicitly) |
| `org.jboss.slf4j:slf4j-jboss-logmanager` | SLF4J bridge for Quarkus logging |

### 3. Package Structure

```
dev.rabauer.banking
  ├── entity/         Account, Transaction, TransactionType
  ├── repository/     AccountRepository, TransactionRepository
  ├── service/        AccountService, TransactionService, InsufficientFundsException
  └── view/           AccountListView, AccountDetailView, DepositWithdrawForm
```

All sources live under `src/main/java/dev/rabauer/banking/`.
Flyway migration scripts live under `src/main/resources/db/migration/`.
Configuration lives in `src/main/resources/application.properties`.

### 4. Database Schema

**`account` table**

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` PK | auto-generated |
| `owner_name` | `VARCHAR(255)` | display name |
| `account_number` | `VARCHAR(20)` | unique, formatted string |
| `created_at` | `TIMESTAMP` | set via `@PrePersist` |

No `balance` column — balance is always derived by summing transactions at query time.

**`transaction` table**

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` PK | auto-generated |
| `account_id` | `BIGINT` FK → `account.id` | many-to-one, source account |
| `target_account_id` | `BIGINT` FK → `account.id` | nullable; set for transfers, null for cash |
| `type` | `VARCHAR(20)` | enum: `DEPOSIT`, `WITHDRAWAL`, `TRANSFER` |
| `amount` | `NUMERIC(19,4)` | signed: positive = money in, negative = money out |
| `description` | `VARCHAR(500)` | optional note |
| `created_at` | `TIMESTAMP` | set via `@PrePersist` |

Schema is managed by Flyway. Migration scripts are placed in `src/main/resources/db/migration/`:
- `V1__create_account.sql` — creates the `account` table.
- `V2__create_transaction.sql` — creates the `transaction` table with two FKs to `account`: `account_id` (NOT NULL) and `target_account_id` (NULL allowed).

Quarkus applies pending migrations automatically on startup (`quarkus.flyway.migrate-at-start=true`).

### 5. Entities

**`entity/Account.java`** — `@Entity`, `@Table(name="account")`.
Fields: `id` (`@Id @GeneratedValue`), `ownerName`, `accountNumber`
(`@Column(unique=true)`), `createdAt` (`LocalDateTime`, `@PrePersist`).
No `balance` field — balance is computed from transactions on demand.
Lazy one-to-many to `Transaction`.
Plain JPA style — does NOT extend `PanacheEntity`.

**`entity/Transaction.java`** — `@Entity`, `@Table(name="transaction")`.
Fields: `id`, `account` (`@ManyToOne(fetch=LAZY)`, source account), `targetAccount`
(`@ManyToOne(fetch=LAZY)`, nullable — set for transfers, null for cash operations),
`type` (`TransactionType`, `@Enumerated(STRING)`), `amount` (`BigDecimal`, signed:
positive for incoming, negative for outgoing from the source account's perspective),
`description` (`String`), `createdAt` (`@PrePersist`).

**`entity/TransactionType.java`** — enum with values `DEPOSIT`, `WITHDRAWAL`, `TRANSFER`.

### 6. Repositories

**`repository/AccountRepository.java`** — `@ApplicationScoped`,
`implements PanacheRepository<Account>`. Custom methods:
`findByAccountNumber(String)`, `findAllOrderedByOwner()`.

**`repository/TransactionRepository.java`** — `@ApplicationScoped`,
`implements PanacheRepository<Transaction>`. Custom methods:
`findByAccount(Account)` (ordered by `createdAt` DESC), `findByAccountId(Long)`,
`calculateBalance(Long accountId)` — issues a simple JPQL aggregate query
(`SELECT SUM(t.amount) FROM Transaction t WHERE t.account.id = :id`)
returning `BigDecimal`. Because amounts are signed, no CASE expression is needed.
Returns `BigDecimal.ZERO` when no transactions exist (null-safe coalesce in Java).

Both repositories inherit the full Panache CRUD API (`persist`, `find`, `list`, `count`, `delete`).

### 7. Services

**`service/AccountService.java`** — `@ApplicationScoped`. Injects `AccountRepository`
and `TransactionRepository`. All mutating methods annotated `@Transactional`.

- `createAccount(String ownerName)` — generates unique account number, persists
  and returns `Account`. No initial balance field.
- `calculateCurrentBalance(Long accountId)` — delegates to
  `TransactionRepository.calculateBalance(accountId)`. Read-only; no `@Transactional`
  required but safe to include.
- `findAll()` — delegates to repository.
- `findById(Long id)` — returns `Optional<Account>`.

**`service/TransactionService.java`** — `@ApplicationScoped`. Injects both repositories.
All mutating methods annotated `@Transactional`.

- `deposit(Long accountId, BigDecimal amount, String description)` — validates
  `amount > 0`, loads account with `LockModeType.PESSIMISTIC_WRITE`, creates
  `Transaction(DEPOSIT, positiveAmount, targetAccount=null)`, persists and returns
  `Transaction`.
- `withdraw(Long accountId, BigDecimal amount, String description)` — validates
  `amount > 0`, calls `TransactionRepository.calculateBalance(accountId)` inside the
  same transaction; checks `currentBalance.compareTo(amount) >= 0`; throws
  `InsufficientFundsException` if not; otherwise creates
  `Transaction(WITHDRAWAL, negativeAmount, targetAccount=null)` and persists it.
  The pessimistic lock on `Account` prevents concurrent withdrawals from racing past
  the balance check.
- `transfer(Long sourceAccountId, Long targetAccountId, BigDecimal amount, String description)` —
  validates `amount > 0`, acquires pessimistic locks on both accounts (always lock
  lower id first to avoid deadlocks), checks balance, creates two `Transaction` records:
  one on the source account with a negative amount (`TRANSFER`, `targetAccount` set)
  and one on the target account with a positive amount (`TRANSFER`, `targetAccount=null`
  or the source — document the convention). Both are persisted atomically.
- `getTransactionHistory(Long accountId)` — delegates to `TransactionRepository`.

All `BigDecimal` arithmetic uses `add`/`subtract` with `RoundingMode.HALF_UP`, scale 4.
No balance is ever written back to the `Account` entity.

**`service/InsufficientFundsException.java`** — unchecked `RuntimeException`.

### 8. Vaadin Views

All views extend `VerticalLayout`, live in `dev.rabauer.banking.view`, and use constructor
injection (`@Inject` on constructor). They are full CDI-managed beans via the
`vaadin-quarkus-extension` bridge.

**`view/AccountListView.java`** — `@Route("")` (root).
- `H2` heading "Accounts"
- `Button("New Account")` → opens `Dialog` with `TextField(ownerName)` + Save button
  calling `AccountService.createAccount`
- `Grid<Account>` with columns: Account Number, Owner, Balance
- Row-click navigates to `AccountDetailView` via
  `UI.getCurrent().navigate(AccountDetailView.class, account.getId())`

**`view/AccountDetailView.java`** — `@Route("account/:id")`,
implements `HasUrlParameter<Long>`.
- `H3` showing owner name + account number
- Current balance as formatted `BigDecimal` label — populated by calling
  `AccountService.calculateCurrentBalance(id)` on every page load and after
  each deposit/withdraw
- `Button("Deposit")` and `Button("Withdraw")` each open a `Dialog` wrapping
  `DepositWithdrawForm`
- `Grid<Transaction>` showing type, signed amount, target account (if set), date
- Balance label and grid both refresh after every deposit/withdraw/transfer

**`view/DepositWithdrawForm.java`** — reusable `FormLayout` (not a route). Constructor
accepts `TransactionType` to toggle label text. Fields: amount (`TextField` validated
to `BigDecimal`), description (`TextField`). Exposes a `Consumer<Transaction>` callback
so the parent view can react without tight coupling.

### 9. Configuration (`application.properties`)

```properties
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/banking
quarkus.datasource.username=banking
quarkus.datasource.password=banking
quarkus.flyway.migrate-at-start=true
quarkus.flyway.locations=classpath:db/migration
quarkus.http.port=8080

# Dev profile override (point to local Docker DB)
%dev.quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/banking
```

Hibernate schema auto-generation is **disabled** — Flyway owns the schema entirely.
Add `quarkus.hibernate-orm.schema-management.strategy=none` to make this explicit.

### 10. Docker Compose

Create `docker-compose.yml` at the project root. It defines two services:

**`db`** — PostgreSQL 16 container.
- Image: `postgres:16`
- Environment: `POSTGRES_DB=banking`, `POSTGRES_USER=banking`, `POSTGRES_PASSWORD=banking`
- Port mapping: `5432:5432`
- Named volume `pgdata` for persistence.

**`app`** — Builds and runs the Quarkus application.
- `build.context`: project root (`.`)
- `build.dockerfile`: `src/main/docker/Dockerfile.jvm` (generated by Quarkus)
- Depends on `db` with `condition: service_healthy`
- `db` uses a `healthcheck` (`pg_isready`) so the app waits for PostgreSQL to be ready
  before starting.
- Environment: overrides datasource URL to `jdbc:postgresql://db:5432/banking` using
  the compose service name as hostname.
- Port mapping: `8080:8080`

The `Dockerfile.jvm` is produced by `mvn package -Pproduction` and lives in
`src/main/docker/`. Quarkus scaffolds it when the project is created via the Quarkus
CLI or the starter; if missing, generate it with `mvn quarkus:generate-code`.

### 11. Running the MVP

1. Build the application JAR: `mvn package -Pproduction`
2. Start everything: `docker compose up --build`
3. Open `http://localhost:8080` — `AccountListView` renders.
4. Dev mode (without Docker for the app): `mvn quarkus:dev`
   (requires PostgreSQL already running locally or via `docker compose up db`)

---

## Verification

- **Smoke**: `mvn quarkus:dev` starts without errors; root route renders accounts grid.
- **Flyway**: log output shows `Successfully applied N migration(s)` on first start; no
  `drop-and-create` is ever executed.
- **Create account**: owner name entered → balance displays `0.0000` (no transactions yet;
  `calculateBalance` returns zero).
- **Deposit 100**: balance becomes `100.0000`; transaction row shows amount `+100.0000`,
  `target_account_id` is null; `account` table row has no balance column.
- **Withdraw 30**: balance becomes `70.0000`; transaction row shows amount `-30.0000`,
  `target_account_id` is null.
- **Withdraw 150**: UI surfaces insufficient funds message; balance unchanged; no new
  transaction row written.
- **Transfer 50** to another account: source shows `-50.0000`, target shows `+50.0000`;
  source transaction has `target_account_id` populated.
- **Docker Compose**: `docker compose up --build` starts both containers; app is
  accessible at `http://localhost:8080` within seconds of the DB health check passing.
- **Unit tests** (post-MVP): `@QuarkusTest` + `@InjectMock` for repositories; cover
  `TransactionRepository.calculateBalance` with mixed deposits/withdrawals, and the
  `TransactionService.withdraw` insufficient-funds path.

---

## Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Entity style | Plain JPA, no `PanacheEntity` | Keeps domain model free of Panache coupling |
| Vaadin version | 24 LTS over 25 | More stable as of Feb 2026; clear upgrade path |
| Schema management | Flyway (`V1__`, `V2__` scripts) | Explicit, version-controlled migrations; survives restarts without data loss |
| Balance storage | Computed from transactions (event-based) | Single source of truth; no balance drift; audit trail is always consistent |
| DI framework | Quarkus CDI only | No Spring; `@ApplicationScoped` / `@Inject` / `@Transactional` throughout |
| Concurrency | Pessimistic lock on `Account` row | Serialises concurrent withdrawals without optimistic retry complexity |
| BOM ordering | Vaadin BOM before Quarkus BOM | Required to avoid JNA `NoClassDefFoundError` at runtime |
| Package name | `dev.rabauer.banking` | Project-specific namespace |
