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
SPRING_PROFILES_ACTIVE=local
GRUN_LOCAL_ADMIN_BOOTSTRAP_ENABLED=false
GRUN_LOCAL_ADMIN_EMAIL=admin@grun.local
GRUN_LOCAL_ADMIN_PASSWORD=change-me-local-only
GRUN_LOCAL_DEMO_SEED_ENABLED=false
GRUN_LOCAL_DEMO_USER_EMAIL=demo.user@grun.local
GRUN_LOCAL_DEMO_USER_PASSWORD=DemoUserPass1!
GRUN_PASSWORD_RESET_EXPIRATION_MINUTES=30
GRUN_PASSWORD_RESET_BASE_URL=http://localhost:8080/reset-password
GRUN_EMAIL_VERIFICATION_EXPIRATION_MINUTES=1440
GRUN_EMAIL_VERIFICATION_BASE_URL=http://localhost:8080/verify-email
GRUN_REFRESH_TOKEN_EXPIRATION_DAYS=30
GRUN_MAIL_PROVIDER=LOG
GRUN_MAIL_FROM_EMAIL=no-reply@grun.local
GRUN_MAIL_FROM_NAME=GRun
GRUN_BREVO_API_KEY=
GRUN_BREVO_API_URL=https://api.brevo.com/v3/smtp/email
GRUN_RATE_LIMIT_ENABLED=true
GRUN_RATE_LIMIT_AUTH_MAX_REQUESTS_PER_MINUTE=20
```

Do not commit `.env`. Commit only `.env.example`.

PostgreSQL Docker volumes keep their original database password. If `.env` is changed after the first container initialization, the existing `grun_pgdata` volume will not automatically change its database password. Keep `.env` stable for local development, or intentionally recreate the local volume when you want a clean database.

### Optional Local Admin Bootstrap

Admin endpoints require an `ADMIN` user. For local development only, enable bootstrap in `.env`:

```env
SPRING_PROFILES_ACTIVE=local
GRUN_LOCAL_ADMIN_BOOTSTRAP_ENABLED=true
GRUN_LOCAL_ADMIN_EMAIL=admin@grun.local
GRUN_LOCAL_ADMIN_PASSWORD=LocalAdminPass1!
```

When the API starts with these values, it creates or updates that local user with the `ADMIN` role. This is only for local development and demo data preparation; production admin users must be provisioned separately.

### Optional Local Demo Seed

For local Swagger testing, enable a small idempotent demo data set:

```env
SPRING_PROFILES_ACTIVE=local
GRUN_LOCAL_DEMO_SEED_ENABLED=true
GRUN_LOCAL_DEMO_USER_EMAIL=demo.user@grun.local
GRUN_LOCAL_DEMO_USER_PASSWORD=DemoUserPass1!
```

When enabled, the API creates or updates:

- one standard demo user
- three verified demo food products
- today's demo food logs for breakfast, snack, and lunch
- today's demo exercise log
- one raw product for the admin review queue

Running it more than once updates the same demo user/products and does not create duplicate logs for the same day.

To remove only local demo data while preserving the PostgreSQL volume and local admin user:

```powershell
.\scripts\cleanup-local-demo.ps1
```

### Local Password Reset

Password reset uses the configured transactional mail provider. In local development, `GRUN_MAIL_PROVIDER=LOG` writes the reset link to the application log:

```text
POST /api/v1/auth/password-reset/request
POST /api/v1/auth/password-reset/confirm
```

The token stored in PostgreSQL is hashed; in local LOG mode the reset link contains the raw local test token. In Brevo mode the backend sends the email through Brevo and does not log the token.

### Local Email Verification

Email verification uses the configured transactional mail provider. New registered users start as unverified and cannot login until their email is confirmed.

```text
POST /api/v1/auth/email-verification/resend
POST /api/v1/auth/email-verification/confirm
```

The verification token stored in PostgreSQL is hashed; in local LOG mode the verification link contains the raw local test token. In Brevo mode the backend sends the email through Brevo and does not log the token.

### Transactional Email Provider

Transactional emails use a configurable delivery service. Local development defaults to `GRUN_MAIL_PROVIDER=LOG`, so password reset and email verification links are written to the application log and no external email is sent.

For production, use Brevo:

```env
GRUN_MAIL_PROVIDER=BREVO
GRUN_MAIL_FROM_EMAIL=no-reply@your-domain.com
GRUN_MAIL_FROM_NAME=GRun
GRUN_BREVO_API_KEY=change-me
GRUN_BREVO_API_URL=https://api.brevo.com/v3/smtp/email
```

Provider credentials must stay in local or deployment secrets and must not be committed.

Before enabling Brevo in production, verify the sender/domain in Brevo and configure SPF, DKIM, and DMARC DNS records for the sending domain. Use a sender address on that verified domain, for example `no-reply@your-domain.com`.

### Rate Limiting

The backend includes an in-memory rate limiter for high-risk authentication endpoints:

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/password-reset/request`
- `POST /api/v1/auth/email-verification/resend`

Local defaults allow 20 requests per minute per client IP and endpoint. For multi-instance production deployments, this should be moved to a shared store such as Redis.

### Food Portions

Food logs accept `portionSize` with an optional `portionUnit`. If `portionUnit` is omitted, the backend treats the value as grams for backward compatibility.

Supported units:

- `GRAM`
- `MILLILITER`
- `SERVING`
- `PIECE`

The backend stores `normalizedPortionGrams` for nutrition calculations. For `SERVING` and `PIECE`, the conversion uses the product's `servingSize` when available, otherwise it falls back to 100 grams.

### Mobile Session Tokens

Login returns a short-lived JWT access token and a long-lived refresh token. Mobile clients should keep users signed in by calling refresh before or after access token expiry instead of asking for email/password again.

```text
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
```

Refresh tokens are stored hashed in the database, rotated on every refresh, and revoked on logout. Password reset revokes active refresh tokens for the user.

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

Temporary admin product UI:

```text
http://localhost:8080/admin-ui/index.html
```

This lightweight page uses the existing admin APIs for login, catalog summary, small CSV import, review queue, product review updates, and audit inspection. For a small pilot import, use:

```text
sample-data/food-products-pilot-small.csv
```

The pilot file currently contains 30 rows and every row must carry one supported market region:

```text
IRL = Ireland
TR  = Turkey
UK  = United Kingdom
```

This is not the final admin panel; it is only a local development tool for visual product review.

### API Versioning

The current API contract is `v1`. Clients should use `/api/v1/...` paths, for example:

```text
POST /api/v1/auth/login
GET /api/v1/products/search
POST /api/v1/food-logs
```

Unversioned `/api/...` route aliases are not exposed. Keeping one path family prevents duplicate Swagger entries and keeps the mobile contract explicit.

Food product search supports market region filtering:

```text
GET /api/v1/products/search?q=milk&region=UK
```

If `region` is omitted, the backend uses the authenticated user's saved `marketRegion`.

The current mobile integration contract is maintained in:

```text
docs/MOBILE_API_CONTRACT.md
```

### Stop Local Services

```powershell
.\scripts\stop-local.ps1
```

The script stops Spring Boot processes started by Maven and stops the PostgreSQL Docker Compose service. The PostgreSQL data volume is preserved.

If Windows blocks command-line inspection for Java processes, the stop script falls back to stopping the process listening on port `8080`.

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

### Local Catalog Cleanup

To remove local non-custom food products that do not match the supported market regions, run:

```powershell
.\scripts\cleanup-local-food-products.ps1
```

This is a local development cleanup script. It deletes dependent local food logs, favorites, meal template items, and review audits for removed catalog products.

## Development Status

Currently under active development.

