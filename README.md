# GRun Calorie Tracker

GRun Calorie Tracker is an AI-powered health and nutrition application that allows users to track daily calorie intake and exercise routines. The backend is built with Java 17, Spring Boot, PostgreSQL, JWT, Flyway, and Maven.

## Features

- User registration and authentication
- Food product search with local catalog and Open Food Facts fallback
- Food and exercise logging
- Daily food and exercise statistics
- Admin product review and duplicate product management
- Exercise catalog with rich metadata for future workout planning
- PostgreSQL setup via Docker
- Swagger/OpenAPI documentation

## Project Docs

- `docs/ROADMAP.md`: current project status and next work items
- `docs/PROGRESS_LOG.md`: technical progress history
- `docs/DATA_SEED_STRATEGY.md`: database seed and catalog data strategy
- `docs/PRODUCT_CATALOG_ADMIN_FLOW.md`: admin product review workflow
- `docs/FOOD_PRODUCT_REVIEW_AUDIT_PLAN.md`: planned audit history model

## Local Setup

### Requirements

- Java 17
- Docker Desktop
- Maven Wrapper included in the project
- PowerShell on Windows

### Environment File

Create a local `.env` file from the committed example:

```powershell
Copy-Item .env.example .env
```

Default local values are:

```env
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres
POSTGRES_DB=grun_calorie_db
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/grun_calorie_db
JWT_SECRET=Q0hBTkdFX01FX0xPQ0FMX0RFVkVMT1BNRU5UX1NFQ1JFVF9LRVlfMTIzNDU2Nzg5MA==
JWT_EXPIRATION_MS=86400000
OPENFOODFACTS_BASE_URL=https://world.openfoodfacts.org
```

Do not commit `.env`. Commit only `.env.example`.

### Start PostgreSQL And API

Start Docker Desktop first, then run:

```powershell
.\scripts\run-local.ps1
```

The script:

- loads `.env` if present
- starts PostgreSQL with Docker Compose
- waits until PostgreSQL is ready
- starts the Spring Boot API

API URL:

```text
http://localhost:8080
```

Swagger UI:

```text
http://localhost:8080/swagger-ui/index.html
```

### Stop Local Services

```powershell
.\scripts\stop-local.ps1
```

The script stops Spring Boot processes started by Maven and stops the PostgreSQL Docker Compose service. The PostgreSQL data volume is preserved.

### Run Tests

```powershell
.\mvnw.cmd clean test
```

### Useful Docker Checks

```powershell
docker ps --filter "name=grun-postgres"
docker logs grun-postgres
docker exec grun-postgres psql -U postgres -d grun_calorie_db -c "select version, description, success from flyway_schema_history order by installed_rank;"
```

## Development Status

Currently under active development.
