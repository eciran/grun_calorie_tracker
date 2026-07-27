# GRun Admin UI

Separate React/Vite admin workspace for internal operations.

## Run Locally

Start the Spring Boot backend first:

```powershell
.\scripts\run-local.ps1
```

Then run the admin UI:

```powershell
cd admin-ui
npm install
npm run dev
```

Open:

```text
http://127.0.0.1:5174
```

The Vite dev server proxies `/api/...` requests to `http://localhost:8080`, so the browser does not need direct CORS changes for local development.

## Initial Scope

- Admin login with backend JWT.
- Dashboard summary.
- Food product review queue.
- User list.
- Subscription feature matrix.
- AI meal draft review queue.
- Admin audit log.
- Notifications.
- System health payload.

The legacy static admin page under `src/main/resources/static/admin-ui` remains available as a temporary local fallback.


## Admin UI quality gate

Run these checks before an admin UI sprint is considered complete:

```powershell
npm run test:foundation
npm run build
```

The shared primitives in `src/AdminPrimitives.tsx` own page status, panels,
tables, pagination, and empty/error states. New screens should extend these
patterns instead of adding independent base components.

## Operator Runbook

1. Start PostgreSQL and the backend with `powershell -ExecutionPolicy Bypass -File ..\scripts\run-local.ps1`.
2. Start the UI with `npm run dev`; use `http://127.0.0.1:5174` and sign in with an assigned admin account.
3. Confirm the role badge and open only sections shown by the backend permission profile.
4. Before a write action, review the target, required reason, preview, and confirmation dialog. Never paste credentials, tokens, health data, or raw provider payloads into notes.
5. After a write action, verify the success state and the Audit Logs entry. For finance, entitlement, access, campaign, catalog publication, and runtime changes, record the support or incident reference in the reason.
6. Use System Health and Settings before escalating a failure. Capture correlation ID, UTC time, affected route, safe summary, and impact; do not capture secrets or full customer payloads.
7. Run `npm run build` followed by `npm run test:production` before an admin release. A failed gate blocks release.

## Role Walkthroughs

- Support: find a user, review Customer 360, add a safe support note, and revoke sessions only with a documented reason. Support cannot change subscriptions or runtime settings.
- Catalog: process product, recipe, contribution, duplicate, image, nutrition, and exercise queues; verify evidence before publish/reject decisions.
- Growth: inspect adoption and retention, preview campaign audience, save a draft, then schedule only after message, consent category, region, language, plan, route, and delivery time are checked.
- Finance: inspect resolved entitlement and provider events, apply a support correction with a reason, verify quota dates, and confirm the audit record.
- Technical: inspect provider/system health, API signals, jobs and incidents; use maintenance mode or rollback only with an incident reference.
- Read only: inspect dashboards, queues, system state, and audits without mutation controls.
- Owner: manage admin roles and all domains; use least privilege and review high-impact audit events daily.

## Incident Playbook

1. Classify severity: SEV-1 service unavailable/security or widespread data risk; SEV-2 major degraded workflow; SEV-3 limited operational defect.
2. Open an incident record in Settings with a sanitized impact summary and escalation owner.
3. For SEV-1, pause risky campaigns/jobs, enable maintenance mode only when user operations are unsafe, and notify the incident owner.
4. Use correlation IDs and aggregate metrics to diagnose. Do not expose raw tokens, secrets, images, health declarations, or full AI/provider payloads.
5. Roll back the latest runtime policy when it caused impact. Code/database rollback follows the deployment runbook and requires backup verification.
6. Resolve only after health, critical user flow, audit trail, and notification status are verified. Record follow-up owner and target sprint.

## Glossary

- Entitlement snapshot: rights preserved for the current paid period.
- Feature matrix: current plan rules applied to new or renewed entitlement periods.
- Runtime policy: audited maintenance, release, rollout, threshold, and escalation settings.
- Rollout: allowlisted exposure by plan, region, user segment, and stable percentage bucket.
- Dead letter: a scheduled job that exhausted normal processing and needs operator review.
- Correlation ID: request identifier used to trace backend logs without copying payloads.
- Customer 360: minimized support view combining account, subscription, activity, and risk context.
- Maker-checker: separate-admin approval for high-impact actions; not yet provider-backed in this release.

## Known Production Dependencies

| Severity | Dependency | Owner | Target |
| --- | --- | --- | --- |
| High | Real MFA provider challenge and recovery flow | Security | Production security phase |
| High | Maker-checker approval for critical finance, entitlement, and runtime writes | Backend/Security | Post-Sprint 11 |
| High | PostgreSQL V176 migration and restore smoke on production-like infrastructure | Platform | Release candidate |
| Medium | GDPR export/deletion metadata queue and processor evidence | Compliance/Backend | Compliance phase |
| Medium | Browser-matrix visual automation beyond Chromium manual QA | Frontend | CI hardening |
| Medium | Admin bundle code splitting; current JS budget is capped at 650 KB | Frontend | Performance phase |

These dependencies are explicit release risks, not silent TODOs. Owners must update severity and target when scope changes.