# Vaadin Banking App

> Companion repository for the YouTube live session **"Guided Coding instead of Vibe Coding in Java"**
> with [Johannes Rabauer](https://github.com/JohannesRabauer) and [Kenny Pflug](https://github.com/feO2x).
>
> **Watch the session:** https://youtube.com/live/vopBYXp9YV0

---

## About the Session

This repository was built live on stream to demonstrate **Guided Coding** — a structured methodology for AI-assisted development developed by Kenny Pflug.

Guided Coding is not about generating as much code as possible. It is about controlling the process:

- Separate planning from implementation
- Define constraints and architecture *before* letting AI write code
- Review AI output with architectural intent
- Refactor systematically instead of reactively
- Maintain long-term code quality while using AI heavily

The stream walks through these principles step by step, using this banking application as a concrete, reproducible example.

---

## Application Overview

A simple banking demo that illustrates a clean domain model with a server-side Vaadin UI backed by Quarkus and PostgreSQL. It supports:

- Creating bank accounts with auto-generated account numbers
- Depositing and withdrawing money
- Transferring money between accounts
- Viewing the full transaction history per account

There is no stored balance column. The current balance is always computed from the transaction log, which gives a full audit trail and avoids any drift between stored and actual values.

---

## Tech Stack

| Component      | Version      |
|----------------|--------------|
| Java           | 21           |
| Quarkus        | 3.20 (LTS)   |
| Vaadin Flow    | 24 (LTS)     |
| Hibernate ORM  | via Quarkus BOM |
| Flyway         | via Quarkus BOM |
| PostgreSQL     | 16           |

---

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker and Docker Compose

---

## Getting Started

### Option 1 — Full stack with Docker Compose

Builds the application and starts everything (database + app) in one command:

```bash
docker compose up --build
```

Open http://localhost:8080 in your browser.

### Option 2 — Local development mode (hot reload)

Start only the database:

```bash
docker compose up db
```

Then, in a second terminal, start the application in Quarkus dev mode:

```bash
mvn quarkus:dev
```

Open http://localhost:8080 in your browser.
The Quarkus Dev UI is available at http://localhost:8080/q/dev.

### Option 3 — Production build

```bash
mvn package -Pproduction
```

This compiles the Vaadin frontend and packages a runnable JAR.

---

## Project Structure

```
src/main/java/dev/rabauer/banking/
    entity/         # JPA entities: Account, Transaction, TransactionType
    repository/     # Panache repositories with custom JPQL queries
    service/        # Business logic: AccountService, TransactionService
    view/           # Vaadin Flow UI views

src/main/resources/
    application.properties
    db/migration/   # Flyway SQL migrations (V1: account, V2: transaction)

ai-plans/           # Guided Coding planning documents (see below)
```

---

## AI Plans and Guided Coding

The `ai-plans/` directory contains structured planning documents that were written **before** any AI-generated code. This is the central artifact of the Guided Coding methodology.

The developer defines the goal, architecture, constraints, and step-by-step plan first. Only then is the AI used as a disciplined implementation engine, working within the boundaries already set.

The plan used during the live session is:

- [ai-plans/0001-quarkus-vaadin-banking-app.md](ai-plans/0001-quarkus-vaadin-banking-app.md)

Reading this file alongside the finished code shows exactly how planning shapes the result and prevents architectural drift.

---

## Authors

- [Johannes Rabauer](https://github.com/JohannesRabauer)
- [Kenny Pflug](https://github.com/feO2x)
