# Local Development

## Requirements

- Java 17
- Docker Desktop
- PowerShell on Windows
- Maven Wrapper, included in the repository

## Setup

Create a local `.env` file:

```powershell
Copy-Item .env.example .env
```

Start local services and API:

```powershell
.\scripts\run-local.ps1
```

The script:

- loads `.env` if present
- starts PostgreSQL and Redis with Docker Compose
- waits until PostgreSQL and Redis are ready
- starts the Spring Boot API

## Local URLs

API:

```text
http://localhost:8080
```

Swagger UI:

```text
http://localhost:8080/swagger-ui/index.html
```

Temporary admin UI:

```text
http://localhost:8080/admin-ui/index.html
```

The local admin UI is a development tool for catalog/review checks. It is not the final production admin dashboard.

## Optional Local Admin Bootstrap

Admin endpoints require an `ADMIN` user. For local development only, enable admin bootstrap in `.env`.

Relevant variables are documented in `.env.example`.

When enabled, the API creates or updates the configured local user with the `ADMIN` role. Production admin users must be provisioned separately.

## Optional Local Demo Seed

For Swagger testing, a small idempotent demo data set can be enabled through `.env`.

When enabled, the API creates or updates:

- one standard demo user
- verified demo food products
- today's demo food logs
- today's demo exercise log
- one raw product for the admin review queue

To remove only local demo data while preserving the PostgreSQL volume and local admin user:

```powershell
.\scripts\cleanup-local-demo.ps1
```

## Password Reset And Email Verification

Local development can use log-based mail delivery. In that mode, password reset and email verification links are written to the application log.

Endpoints:

```text
POST /api/v1/auth/password-reset/request
POST /api/v1/auth/password-reset/confirm
POST /api/v1/auth/email-verification/resend
POST /api/v1/auth/email-verification/confirm
```

Stored reset and verification tokens are hashed in PostgreSQL.

## Food Product Pilot Data

Small pilot import file:

```text
sample-data/food-products-pilot-small.csv
```

Current supported market regions are defined in code and import validation. Use the current enum values from the backend rather than hardcoding old region names in external files.

## Local Catalog Cleanup

To remove local non-custom food products that do not match supported market regions:

```powershell
.\scripts\cleanup-local-food-products.ps1
```

This deletes dependent local food logs, favorites, meal template items, and review audits for removed catalog products.

## Docker Checks

```powershell
docker ps --filter "name=grun-postgres"
docker logs grun-postgres
docker exec grun-postgres psql -U postgres -d grun_calorie_db -c "select version, description, success from flyway_schema_history order by installed_rank;"
```

## Stop Services

```powershell
.\scripts\stop-local.ps1
```

The script stops Spring Boot processes started by Maven and stops local Docker Compose services while preserving data volumes.
