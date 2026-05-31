# Configuration

This document explains how local and deployment configuration should be managed.

## Environment Files

For local development, create `.env` from the committed example:

```powershell
Copy-Item .env.example .env
```

Do not commit `.env`. Commit only `.env.example`.

## Local Defaults

Local defaults live in:

```text
.env.example
```

Use that file as the source of truth for required variables. The README intentionally does not duplicate the full environment list because it can expose implementation detail and make the public repository harder to review.

## Secret Handling

These values must be treated as secrets or sensitive operational configuration:

- `JWT_SECRET`
- `POSTGRES_PASSWORD`
- `GRUN_LOCAL_ADMIN_PASSWORD`
- `GRUN_LOCAL_DEMO_USER_PASSWORD`
- `GRUN_BREVO_API_KEY`
- `GRUN_REVENUECAT_WEBHOOK_AUTHORIZATION`
- production database credentials
- production mail/store/provider credentials

Local placeholder values are acceptable in `.env.example`, but real values must stay in `.env` or deployment secret stores.

## PostgreSQL Volume Note

PostgreSQL Docker volumes keep their original database password. If `.env` is changed after the first container initialization, the existing local volume will not automatically change its database password.

For local development, keep `.env` stable or intentionally recreate the local database volume when starting from a clean database.

## Mail Provider

Local development defaults to log-based mail delivery. Password reset and email verification links are written to the application log.

Production should use Brevo:

```text
GRUN_MAIL_PROVIDER=BREVO
```

Before enabling Brevo in production:

- verify the sender/domain in Brevo
- configure SPF, DKIM, and DMARC DNS records
- use a sender address on the verified domain
- keep the Brevo API key outside Git

## Rate Limiting

Local development can use in-memory rate limiting.

Production should enable Redis-backed shared rate limits:

```text
GRUN_RATE_LIMIT_REDIS_ENABLED=true
```

The local run script starts Redis through Docker Compose. If Redis is enabled but unavailable, the backend falls back to the in-memory limiter and logs a warning.

## RevenueCat

RevenueCat webhook authorization and product ids should be configured through environment variables or deployment secrets.

Backend smoke checks:

```powershell
.\scripts\smoke-revenuecat-config.ps1 -BaseUrl "http://localhost:8080" -AdminToken "<admin-jwt>"
.\scripts\smoke-revenuecat-webhook.ps1 -BaseUrl "http://localhost:8080" -WebhookAuthorization "<revenuecat-auth-header>" -UserId 1
```

Real App Store / Google Play sandbox purchase tests require a mobile build using the RevenueCat SDK.
