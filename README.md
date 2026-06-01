# GRun Calorie Tracker

GRun Calorie Tracker is a Java 17 / Spring Boot backend for a mobile-first calorie, food logging, exercise, health integration, and subscription-based wellness application.

The backend currently focuses on:

- JWT authentication with refresh tokens
- Email verification and password reset
- Food product search with local catalog and Open Food Facts fallback
- Food logging with portion-unit normalization
- Exercise logging
- User onboarding and calorie goal calculation
- Subscription entitlement and RevenueCat webhook support
- Health integration backend contracts
- Admin catalog/review operations
- GDPR export/delete and legal consent history
- Flyway-managed PostgreSQL schema
- Swagger/OpenAPI documentation

## Quick Start

Requirements:

- Java 17
- Docker Desktop
- PowerShell on Windows
- Maven Wrapper, included in this repository

Create a local environment file:

```powershell
Copy-Item .env.example .env
```

Start local PostgreSQL, Redis, and the Spring Boot API:

```powershell
.\scripts\run-local.ps1
```

Swagger UI:

```text
http://localhost:8080/swagger-ui/index.html
```

Temporary local admin UI:

```text
http://localhost:8080/admin-ui/index.html
```

Run tests:

```powershell
.\mvnw.cmd clean test
```

Stop local services:

```powershell
.\scripts\stop-local.ps1
```

## Documentation

Use these documents instead of keeping operational detail in the README:

- [Local Development](docs/LOCAL_DEVELOPMENT.md)
- [Configuration](docs/CONFIGURATION.md)
- [Mobile API Contract](docs/MOBILE_API_CONTRACT.md)
- [Mobile Subscription Contract](docs/MOBILE_SUBSCRIPTION_CONTRACT.md)
- [RevenueCat Sandbox Test Flow](docs/REVENUECAT_SANDBOX_TEST_FLOW.md)
- [Brevo Smoke Test Runbook](docs/BREVO_SMOKE_TEST_RUNBOOK.md)
- [Food Database Pilot Import Runbook](docs/FOOD_DATABASE_PILOT_IMPORT_RUNBOOK.md)
- [Roadmap](docs/ROADMAP.md)
- [Progress Log](docs/PROGRESS_LOG.md)

## Security Notes

- Do not commit `.env`.
- Commit only `.env.example`.
- Keep API keys, JWT secrets, mail provider credentials, store secrets, and production passwords in local or deployment secret stores.
- Local bootstrap/demo credentials are for development only and must not be reused in production.

## API Versioning

The current public API contract is `v1`. Clients should use `/api/v1/...` paths only.

Examples:

```text
POST /api/v1/auth/login
GET /api/v1/products/search
POST /api/v1/food-logs
```

Unversioned `/api/...` aliases are not exposed.

## Development Status

This project is under active development. The backend is ready for first-pass mobile/frontend integration. Public production release still requires external provider validation, AWS environment setup, and food catalog scaling.

Latest verified backend regression baseline:

```text
365 tests, 0 failures
```
