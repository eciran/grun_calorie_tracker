# AWS Staging Owner Access Record

Date: 2026-08-11
Environment: `staging`
Region: `eu-west-1`

## Owner identities

- Primary owner: `emrahciranie@gmail.com`
- Recovery owner: `root-owner@gruncalorietracker.com`
- Admin URL: `https://api-staging.gruncalorietracker.com/admin-ui/index.html`
- Primary owner login was verified successfully with role `OWNER` after bootstrap was disabled.
- MFA is not enrolled yet. Enroll MFA from the admin security screen before using sensitive owner operations.

## Credential storage

Plaintext passwords are intentionally not recorded in this repository.

- Primary owner password secret:
  `arn:aws:secretsmanager:eu-west-1:911291530432:secret:grun/staging/owner-primary-password-J47VIi`
- Recovery owner password secret:
  `arn:aws:secretsmanager:eu-west-1:911291530432:secret:grun/staging/owner-recovery-password-JvzVPU`

The owner accounts were created once with ECS task definition revision `18`. ECS task definition revision `19` is the active hardened revision:

- `GRUN_OWNER_BOOTSTRAP_ENABLED=false`
- Owner password secrets are not injected into the running application container.
- The secrets remain in AWS Secrets Manager for controlled recovery and rotation.

Do not add owner passwords to Git, application YAML, task definition environment variables, tickets, screenshots, or this document.

## Staging database

- Engine: PostgreSQL on Amazon RDS
- Host: `grun-staging-db.cbkse8ooyjeq.eu-west-1.rds.amazonaws.com`
- Port: `5432`
- Database: `grun_calorie_db`
- Database user: `grun_admin`
- Public access: disabled
- Password source: AWS Secrets Manager secret `grun/staging/db-credentials`

## Catalog verification

A read-only query was executed inside the staging VPC on 2026-08-11.

| Scope | Product count |
| --- | ---: |
| Total | 49,701 |
| UK and Ireland (`UK_IE`) | 25,000 |
| European Union (`EU`) | 22,960 |
| Turkey (`TR`) | 1,594 |
| Global (`GLOBAL`) | 146 |
| Region not assigned | 1 |
| Raw imported | 49,555 |
| Verified | 146 |

Conclusion: the staging database does **not** contain the latest planned `150k+` catalog. That dataset must go through the existing rehearsal, validation, import, and post-import count checks before staging activation.

## Recovery and rotation procedure

1. Confirm the operator is authenticated through the approved AWS SSO profile.
2. Rotate the relevant Secrets Manager secret without exposing the value in command history or logs.
3. Use the controlled admin password reset flow for an existing owner. Do not re-enable bootstrap to overwrite an existing account; bootstrap is create-if-absent only.
4. If both owners are inaccessible, follow an approved break-glass procedure with an audited, temporary task revision.
5. After recovery, disable bootstrap again, remove password secret injection from the runtime task, verify owner login, and enroll or re-enroll MFA.
6. Record only the date, operator, affected secret reference, reason, and verification result. Never record the new secret value.

## Required next checks

- Enroll MFA for the primary owner and store recovery codes outside Git in an approved password manager.
- Confirm the recovery owner login and enroll separate MFA credentials.
- Import the approved production catalog bundle into staging only after the catalog automation reports it release-ready.
- Re-run exact total, market-region, verification-status, duplicate, and rollback checks after import.
