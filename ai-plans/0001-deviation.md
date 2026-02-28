# 0001-deviation — Deviations from Plan 0001

Documents every place where the current implementation differs from the original
plan `0001-quarkus-vaadin-banking-app.md`. Deviations are grouped by area.

---

## 1. Repositories

### 1.1 CriteriaBuilder used instead of JPQL strings / Panache shortcuts

**Plan:**
- `AccountRepository.findByAccountNumber` — use Panache shorthand `find("accountNumber", accountNumber).firstResultOptional()`.
- `AccountRepository.findAllOrderedByOwner` — use Panache shorthand `list("ORDER BY ownerName ASC")`.
- `TransactionRepository` methods (`findByAccountId`, `findByAccount`, `calculateBalance`) — use plain JPQL strings via `em.createQuery(String, Class)`.
- Plan explicitly called out `calculateBalance` as issuing a "simple JPQL aggregate query `SELECT SUM(t.amount) FROM Transaction t WHERE t.account.id = :id`".

**Current:**
All six query methods in both repositories use the JPA `CriteriaBuilder` API exclusively. No JPQL string literals remain.

**Impact:** Functionally equivalent. Type-safe and refactor-safe, but more verbose and further from the plan's stated intent.

### 1.2 `AccountRepository` now injects `EntityManager`

**Plan:** `AccountRepository` was described as a pure Panache repository; no `EntityManager` injection was planned for it.

**Current:** `AccountRepository` has `@Inject EntityManager em` because `CriteriaBuilder` is obtained from it (consequence of deviation 1.1).

---

## 2. `pom.xml`

### 2.1 `vaadin-jandex` dependency missing

**Plan:** `com.vaadin:vaadin-jandex` listed explicitly as a required dependency.

**Current:** Not present in `pom.xml`. The Jandex index for Vaadin components is not explicitly pulled in.

### 2.2 Additional Maven plugins

**Plan:** "quarkus-maven-plugin as the sole build plugin."

**Current:** `maven-compiler-plugin` (3.13.0) and `maven-surefire-plugin` (3.2.5) are also configured in `<build><plugins>`.

### 2.3 `production` profile with `flow-maven-plugin`

**Plan:** "No separate Vaadin plugin is needed; the Vaadin frontend build is embedded inside the extension."

**Current:** A `production` profile is present that includes `com.vaadin:flow-maven-plugin` with `prepare-frontend` and `build-frontend` goals, and pulls in `vaadin-core` with the `vaadin-dev` exclusion.

---

## 3. `application.properties`

### 3.1 Hibernate property key differs from plan

**Plan:** `quarkus.hibernate-orm.schema-management.strategy=none`

**Current:** `quarkus.hibernate-orm.database.generation=none`

The property name used is the older Quarkus 3.x key; the plan specified the newer `schema-management.strategy` key introduced in Quarkus 3.x LTS. Both suppress DDL generation, but the key differs.

---

## 4. Views — `AccountDetailView`

### 4.1 Route pattern and parameter name changed

**Plan:** `@Route("account/:id")`, implementing `HasUrlParameter<Long>`.

**Current:** `@Route("account/:accountId(\\d+)")` with a regex constraint. Implements `BeforeEnterObserver` and reads the parameter via `event.getRouteParameters().get("accountId")`.

`HasUrlParameter` is not used.

### 4.2 Navigation from `AccountListView` uses `RouteParameters`

**Plan:** `UI.getCurrent().navigate(AccountDetailView.class, account.getId())` (Long overload, compatible with `HasUrlParameter<Long>`).

**Current:** Navigation uses `ui.navigate(AccountDetailView.class, new RouteParameters("accountId", String.valueOf(account.getId())))` — a consequence of the parameter name change in deviation 4.1.

### 4.3 Transfer `Dialog` built inline, not through `DepositWithdrawForm`

**Plan:** `Button("Transfer")` is listed in `AccountDetailView`, and `DepositWithdrawForm` is described as accepting a `TransactionType` to toggle labels (implying it also covers the transfer case). The plan left the convention for `targetAccount` on the credit transaction undocumented ("null or the source — document the convention").

**Current:** The transfer dialog (`openTransferDialog`) is constructed entirely inline in `AccountDetailView`, adding a `ComboBox<Account>` for target-account selection outside of `DepositWithdrawForm`. The credit transaction sets `targetAccount = source` (the convention the plan left open).

---

## 5. Views — `AccountListView`

### 5.1 Extra `H1` heading

**Plan:** Layout starts with an `H2` heading "Accounts".

**Current:** An `H1("Banking App")` is placed above the `H2("Accounts")`.

### 5.2 Balance formatted with 2 decimal places and € symbol

**Plan:** Verification section shows balances as `0.0000`, `100.0000`, `70.0000` (4 decimal places, no currency symbol).

**Current:** Balances are formatted to 2 decimal places with a trailing " €" (e.g. `100.00 €`). This applies in both `AccountListView` and `AccountDetailView`.

### 5.3 Grid has an extra "Created" column

**Plan:** Grid columns: Account Number, Owner, Balance.

**Current:** A fourth column, "Created" (`createdAt`), is rendered after Balance.

---

## 6. Views — `DepositWithdrawForm`

### 6.1 No `Consumer<Transaction>` callback

**Plan:** "Exposes a `Consumer<Transaction>` callback so the parent view can react without tight coupling."

**Current:** `DepositWithdrawForm` exposes only `getAmountValue()` and `getDescriptionValue()` accessors. The callback pattern is not implemented; all action logic is wired inline in the dialog `Runnable` lambdas inside `AccountDetailView`.

---

## 7. Service Layer

### 7.1 All `AccountService` methods are `@Transactional`

**Plan:** "All mutating methods annotated `@Transactional`." Read-only methods (`calculateCurrentBalance`, `findAll`, `findById`) did not require it, though the plan allowed it for `calculateCurrentBalance`.

**Current:** Every public method in `AccountService` carries `@Transactional`, including `findAll()` and `findById()`.

### 7.2 Account number format not specified in plan

**Plan:** "generates unique account number" — no format defined.

**Current:** Numbers follow the pattern `DE{yyMMdd}{4-digit-random}` (e.g. `DE2602261234`), with a collision-retry loop. This is an unspecified implementation detail.
