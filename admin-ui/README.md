# GRun Admin UI

Separate React/Vite admin workspace for internal operations.

## Deployment hold

User instruction (2026-09-17): no deployment to staging or production until all planned work is completed and the user has tested locally. Local source edits, isolated tests and local build artifacts are the current scope. No active environment is to be restarted or migrated as part of these deliveries.

## Common TR/EN controls — local delivery

Shared refresh/load statuses, pagination (including accessible names and locale-aware numbers), collapse controls, default empty/loading/error states and confirmation-dialog default labels now follow the selected language. The session-expiry warning also translates its countdown and idle/maximum-duration explanations without changing session timing or activity behavior. Explicit page-supplied titles, messages and action labels remain the responsibility of each domain page; this does not mark all domain content as translated.

Validation: TypeScript/Vite build, production contract/session tests and the mocked-API V2 browser suite passed. Browser coverage verifies English → Turkish → English controls without losing the selected audit filter, disabled previous-page behavior, mobile layouts, route navigation and owner-only error access. No deployment or database migration was performed. Next planned domain work is users/catalog/product management and barcode flows.

## Unified work inbox — local delivery

`/admin/inbox` now combines unread operational notifications, pending product-label reviews, submitted OCR product candidates, rejected refundable AI requests, owner approvals, failed owner-alert deliveries, and investigating/reopened error groups. Each source is requested only when the active admin already has its read permission; owner-only sources additionally require the OWNER role. Items share priority, status, waiting time, and next-action columns and open the existing specialist workspace for resolution.

The inbox reads at most ten open items per source. Persistent personal assignment, following, and cross-domain SLA storage are not present in the existing domain models and were not invented as a parallel task database. Its work controls, notification filters, metrics, distribution, routing guide, table actions and empty states are bilingual; Chrome covers EN/TR switching without losing the work sources. Build, production contracts, and the mocked Chrome role/source/navigation flow pass. No deployment, service restart, or database migration was performed.

## Product barcode workflow — first local slice

The main Product Review page now includes a responsive TR/EN barcode workspace. Admins can type a GTIN, use a USB/Bluetooth scanner that acts as a keyboard, or start the browser camera when the native `BarcodeDetector` API is available. GTIN-8, UPC-A, EAN-13 and GTIN-14 lengths and check digits are validated locally while preserving leading zeroes. Camera absence and permission denial have explicit fallbacks.

A valid scan searches the existing admin catalog review endpoint without importing external product data. An exact normalized-barcode match opens the existing product detail; a missing match links to Product Intake with the barcode prefilled so the normal internal-review workflow remains in force. Repeated camera detection stops after the first value. Barcode mutation is intentionally still pending: collision handling, audit reason and old/multiple-barcode policy require a dedicated backend contract before edits can be enabled.

Validation passed: TypeScript/Vite build, the complete production UI contract suite, dedicated GTIN normalization/check-digit tests, and the mocked-API V2 browser suite covering invalid, found and missing barcodes at mobile width. Physical camera/device coverage remains a local acceptance item. No deployment, service restart or database migration was performed.

The second local slice adds guarded barcode replacement inside Product Review details. Only profiles with `CATALOG_MANAGE` see the editor. The backend requires a valid GTIN and a non-empty reason, rejects a barcode already owned by another product, relies on the existing unique normalized-barcode index for concurrent races, evicts product caches, and records old/new values, operator, time and reason as `BARCODE_CHANGE`. The current model supports one active barcode per product: replacement removes the old value from lookup while preserving it in audit history; no barcode is silently moved or product silently merged. Targeted service/controller/permission tests, the full production UI suite, build, and mocked browser confirmation/mutation flow passed. No migration was needed or applied.

## Customer 360 user-context handoff — local slice

The Customer 360 subscription tab now opens `/admin/subscriptions/access` with the selected user's numeric id and display email in the query string. The destination initializes its user search and effective-access preview from that context, loads the exact user's feature and subscription endpoints, and preserves the selection across reloads. The id is authoritative; the email is only a display/search hint. Invalid, non-positive and unsafe integer ids are ignored.

A TR/EN context banner explains the source of the selection. Its clear action removes the selection and strips the query string without navigating away. Navigation and Customer 360 contract tests cover serialization and validation. The mocked-API Chrome suite covers the real page transition, exact-user API calls, reload persistence and clearing. TypeScript/Vite build also passes. Product-context handoff and restoring user-list filters on return remain later Phase 3 work. No deployment or live service/database action was performed.

Customer 360's AI tab now uses the same URL identity contract for `/admin/ai`. The backend AI request list accepts an exact `userId` alongside request type, status, refundable-only and pagination filters. The AI page clearly separates the user-filtered request list from system-wide summary metrics, preserves the context after reload, and returns to the unfiltered list when the TR/EN context banner is cleared. The email query value remains display-only. Targeted controller/service tests, repository startup validation, the full production UI suite, build and mocked Chrome transition/reload/clear coverage passed. No migration was required or applied.

User-list working state is now URL-backed. Applied search, account, plan, region, language, verification and activity filters, pagination and page size survive a Customer 360 handoff. Opening a row stores its validated numeric user id in the current history entry; browser Back from subscription or AI restores the same list and reopens that user. Closing the modal removes only the selected-user parameter. Filter changes use `replaceState`, so normal filtering does not flood browser history. Build, the full production UI suite and a mocked Chrome search/plan/handoff/back regression passed. Product-context transitions remain the next Phase 3 slice.

Product review working state is also URL-backed across queue, image, nutrition and rejected-product pages. Validated filters, pagination and the open numeric product id survive reloads. Exact barcode lookup narrows the list to that barcode and clears conflicting filters before opening the product, so the detail can be reconstructed from the backend response after refresh. Closing the detail removes only `productId`. Build, the production UI suite, workbench contract checks and mocked Chrome barcode/query/product-id/reload coverage passed. Phase 3 URL context now covers users, subscription access, AI operations and product review; finance and owner-approval usability is next.

## Owner approval center — local slice

Critical approvals now have a dedicated lazy-loaded `/admin/approvals` page instead of being embedded in the admin-team page. Only an OWNER can see or open this route; non-owner deep links are redirected before the approval API is called. The existing backend remains authoritative: listing and decisions require `ADMIN_TEAM_MANAGE`, approval/rejection requires a fresh MFA re-authentication token, and financial makers only create requests through the existing allowlisted action contract.

The page and queue provide TR/EN headings, instructions, filters, columns, payload disclosure and decision controls. The warning explains that approval can execute the request and that target, reason and whitelisted payload must be checked first. The admin-team page no longer duplicates this queue. Validation passed for 57 direct page routes, 35 lazy modules, the full production UI suite, and mocked Chrome owner loading/non-owner denial. No approval was executed, and no real financial record, service or database was changed.

Approval working state is now URL-backed: status, pagination, page size and selected approval id survive refresh. The selected record separates request, decision and execution outcome, labels financial versus operational impact, and summarizes allowlisted subscription/quota/plan-feature fields in TR/EN. Mocked Chrome coverage verifies select → URL → reload → restored detail → close without executing MFA or a real approval. The next slice will connect approval-creation notices in finance/AI pages to this addressable owner record.

Finance and AI approval creation notices now retain the backend approval response and show its real record number. Owners receive a direct link to the pending record; non-owner makers see the traceable number without an unusable owner-route action. This covers subscription updates, quota reset/grant/refund, entitlement-matrix application and plan-feature changes. Build, production UI contracts and mocked Chrome request → notice → owner-record navigation passed. Other maker-checker sources remain the next slice.

Notification campaign scheduling and runtime policy updates now use the same traceable approval notice. Both retain the returned approval DTO, show the record number, and expose the owner-only link without changing the active campaign/runtime state before approval. Build, the full production suite and dedicated campaign/runtime source contracts passed. Protected notification definitions and reminder-policy approval sources remain next.

Protected meal-reminder/subscription copy, subscription-notification policy, meal-reminder publication, reopening and test-send requests now retain and expose their real approval record as well. Emergency stop remains an immediate audited safety control and is explicitly excluded from approval-notice handling. Build, the full production suite and the expanded reminder/notification contract passed. The next planned domain review is AI/OCR operations usability and missing states.

AI operations are now split across `/admin/ai`, `/admin/ai/requests`, and `/admin/ai/policy`. Overview metrics/economics, request/OCR review/refunds, and provider/model/budget controls have independent URLs and navigation tabs. Each mode enables only its relevant API data source. Customer 360 opens the request workspace with the exact user filter and preserves it through reload/clear. The panel now has 60 routes; build, production contracts, MockMvc route serving and mocked Chrome handoff coverage passed.

AI request inspection is now addressable with `requestId` and restores after reload. Closing it removes only that selection while preserving any user context. The read-only detail leads with a responsive TR/EN lifecycle strip for source → OCR/AI extraction → user decision → final state, followed by the existing ingredient, nutrition, confirmation, correction, refund and failure evidence. Build, production contracts and mocked Chrome direct-link/reload/close coverage passed.

## Owner Error Center — H1 details

Open `/admin/system/errors` as OWNER. The new page is TR/EN and offers date/time, exact HTTP status, 4xx/5xx group, method, exact route template, UUID correlation ID and recognized error-code filters; paginated list, event details, current-page distribution and collector health. Other admin roles cannot see the page or call its list/detail/health endpoints (both the authorization filter and method security enforce OWNER).

`V266__owner_error_center.sql` adds a separate event table. No existing application database has been migrated in this work. The outer servlet filter records completed API 400–599 responses, including security responses and final ERROR/ASYNC dispatches. 2xx/3xx are excluded. Exception advice observes typed codes and application stack locations without changing the response. Raw URLs/query values, body, headers, messages, stack messages and user identifiers are not stored. Routes come from registered Spring templates, unmatched paths become `/api/[unmapped]`, and only UUID-format correlation IDs and `ApiErrorCode` values are retained. Details give status explanations plus code/class/method/line when available; they do not claim a root cause that was not captured.

Persistence runs on one dedicated worker: 2,000 queued events, at most 100 per pass, up to three write attempts, idempotent event keys, a 3-second SQL statement timeout and incremental retention cleanup. Default retention is 30 days (`grun.error-center.retention-days`, clamped 1–365); row target is 100,000 (`grun.error-center.max-rows`, clamped 1,000–1,000,000). Cleanup removes at most 1,000 old and 1,000 excess rows per active pass, or once per minute when idle. The row target is incremental, not an instantaneous hard cap. There is no archive in H1. Only the newly introduced error-event table is cleaned.

The queue and collector counters are process-local and reset on restart; queued events can be lost on shutdown. Successfully written events survive restart. Write/cleanup failures, dropped events, pending count and last success/failure are visible in the owner page. The recorder does not throw persistence failures into the original request. A full backend/database outage cannot be made observable through this same database alone.

Local validation: `OwnerErrorCaptureFilterTest`, `OwnerErrorContainerTest`, `OwnerErrorMetadataTest`, `OwnerErrorStoreTest`, `OwnerErrorAuthorizationTest`, and the 60-route `AdminPageControllerTest`; production UI gates and fixture browser tests. H2 file-backed migration/persistence/filter/idempotency/retention tests and isolated Tomcat final-status/async tests passed. A disposable PostgreSQL 16 instance applied all 268 migrations through V273 and passed the owner-operations schema, checksum, ownership-concurrency and historical-backfill contracts. Applying migrations to the existing local database and the user's local end-to-end acceptance remain pending. No test intentionally sends failures to an active environment.

Local acceptance checklist (after starting the local app with its local database): sign in as OWNER, open the page, trigger a safe local invalid API request and wait a few seconds, refresh/filter/open its detail, restart the local backend and confirm the saved event remains, then verify another admin role cannot access the page/API. Check that sensitive input values are absent from persisted details. Existing errors from before this feature are not backfilled from Catalina.

Pending: user/entity linkage, safe field-validation details, reverse-proxy delivery/buffering, mobile-client submission, broader critical-event producers, and error-group lifecycle workflows. Source/version fields, admin-web network telemetry, guarded proxy intake, owner alert operations, and the daily summary are now implemented locally in the later sections below. This still does not prove complete observability across the system.

AI request review keeps request type, status, refundable-only mode, page, page size, Customer 360 user context, and the open request ID in `/admin/ai/requests` query parameters. Reloading a copied URL restores the same workspace, and closing the inspection preserves the list state.

New AI history rows now persist the active request correlation ID. Owner inspection shows that ID and links to `/admin/errors?correlationId=...`; older rows stay unlinked when no real ID exists. Migration `V267__link_ai_requests_to_error_correlation.sql` defines the field/index and has not been applied to an existing application database.

Product Intake details also expose persisted nutrition-label OCR comparison runs. The workbench compares local parser v3/v4, optional fallback-provider, and user-confirmed fields while showing match rates, nutrition-basis checks, latency, stored estimated cost, model, and correlation ID. Owner-only correlation links open Error Center; evidence images continue to use the existing short-lived private URL flow.

Critical backend 5xx events now feed a persistent owner-alert outbox after Error Center storage succeeds. Events are grouped by status/method/route/hour, retried with bounded backoff, localized to the configured primary owner's TR/EN preference, and store only the delivery exception type on failure. Delivery is disabled by default with `GRUN_OWNER_ALERTS_ENABLED=false`; migration `V268__owner_operational_alert_outbox.sql` has not been applied to an existing application database.

The owner-only `/admin/reports/owner-alerts` page exposes that outbox as a separate workspace. Status/category filters and pagination survive reload through the URL; the table shows occurrence count, first/last occurrence, attempts, next attempt or sent time, safe failure type, and a link to the related admin record. Delivery actions use the guarded workflow described below and never send email directly from the HTTP request.

Committed owner approval requests also create durable outbox entries. Financial actions are classified as `FINANCIAL_APPROVAL/CRITICAL`; campaign, runtime and notification operations use `OPERATIONAL_APPROVAL/HIGH`. The event is emitted inside the approval transaction and consumed only after commit, so rolled-back requests do not create alerts. The alert contains only the approval ID and action type and links to `/admin/approvals?approvalId=...`; maker identity, reason, target and payload are excluded.

Owner alert operations now support two guarded transitions. A `FAILED` delivery can be reset to `RETRY`; a `SENT` or `FAILED` record can be marked `ACKNOWLEDGED`. Pending delivery cannot be suppressed. Both operations require an `OWNER_ALERT_ACTION` MFA proof, a written reason, a pessimistic row lock and an immutable admin audit entry. Requeueing resets delivery attempts but does not send mail inside the HTTP request; the existing outbox worker performs delivery.

Migration `V269__allow_owner_alert_action_audits.sql` extends the audit check constraints for these two actions and their target type. It has been prepared locally and has not been applied to an existing application database.

The same page includes an addressable daily summary for the selected report date. It reports backend error groups/occurrences, financial and operational approval requests, the current pending approval backlog, decisions made that day, alert delivery outcomes, and the error-group workflow. The workflow card shows groups first observed on the selected date, the current investigating backlog, and groups whose latest lifecycle update on that date left them resolved or reopened. These values are also included in the bilingual `DAILY_SUMMARY` outbox message. At 23:55 in the configured zone, the scheduler creates one outbox record per date. Defaults are `GRUN_OWNER_ALERTS_DAILY_REPORT_CRON=0 55 23 * * *` and `GRUN_OWNER_ALERTS_DAILY_REPORT_ZONE=Europe/Dublin`; email delivery still follows `GRUN_OWNER_ALERTS_ENABLED=false` by default.

## Error source telemetry — H2 partial delivery

Migration `V270__add_owner_error_sources.sql` adds `BACKEND`, `PROXY`, `ADMIN_WEB`, and `MOBILE` source metadata and permits a null HTTP status for network/timeout observations. It is prepared locally and has not been applied to an existing application database.

Authenticated admin requests keep at most 20 sanitized network/timeout observations in tab memory and submit one after connectivity returns. The payload contains only source, failure kind, method, a query-free masked route, timestamp, duration, platform, and app version. It contains no bearer token, identity, headers, body, query, or exception message. Reloading the tab intentionally discards unsent observations.

`POST /api/v1/error-telemetry/proxy` accepts only 502–504 and requires `X-Proxy-Telemetry-Key` to match `GRUN_ERROR_CENTER_PROXY_INGEST_KEY`; values shorter than 24 bytes leave ingestion disabled. The proxy must supply a stable UUID event key for idempotent storage. Reverse-proxy buffering/retry and mobile client submission are still integration work, so H2 is not complete.

The executable mobile application and managed reverse-proxy configuration are absent from this workspace. `docs/ERROR_TELEMETRY_EDGE_INTEGRATION_CONTRACT.md` defines the producer payload, privacy boundary, retry/buffering behavior and acceptance evidence for those external repositories. Local tests verify authenticated `MOBILE` observations, metadata sanitization, proxy shared-secret enforcement and stable event keys; these tests do not substitute for real proxy or device delivery.

## Error grouping — H3 read-only slice

`GET /api/v1/admin/errors/groups` is OWNER-only and calculates the most frequent fingerprints from durable events. A fingerprint uses source, nullable HTTP status, method, sanitized route template, and error code. Results include occurrence count, first/last seen, maximum duration, and distinct app-version count. The query accepts a bounded date range, an optional source, and a 1–50 limit; the owner page currently requests the top 10.

Grouping is computed from retained events and requires no additional migration. The responsive TR/EN aggregate table was the first read-only H3 slice; the guarded lifecycle extension below adds persistent state and audit history.

## Error-group lifecycle — H3 guarded workflow

Migration `V271__owner_error_group_lifecycle.sql` stores the latest lifecycle state, reason, owner identity, and update time for the SHA-256 fingerprint. `V272__allow_owner_error_group_audits.sql` extends the immutable audit allowlists. `V273__owner_error_group_state_history.sql` stores each accepted transition for reporting. None of these migrations has been applied to an existing database.

Allowed transitions are `NEW → INVESTIGATING → RESOLVED → REOPENED → RESOLVED`. Every mutation requires an 8–500 character reason and a fresh MFA proof bound to `OWNER_ERROR_ACTION`. The backend recomputes the fingerprint, verifies that matching retained events exist, rejects invalid transitions, and writes `OWNER_ERROR_GROUP_STATUS_UPDATE` audit data with the old/new state and correlation ID. The owner UI performs this flow through the shared MFA dialog. Assignment and a separate multi-note history are not included; full change history remains available through immutable audits.

The lifecycle is connected to the owner daily summary. “New” is derived from the first retained occurrence, so retention can affect old historical dates. “Investigating” is the current backlog rather than a historical snapshot. “Resolved” and “reopened” are exact accepted transition counts from the append-only lifecycle history for the selected date. The transition, latest state, and immutable admin audit write share the lifecycle service transaction.

## ADMIN UI V2 — Page Foundation (2026-09-17)

### Independent page URLs (2026-09-17)

The canonical addresses are now `/admin`, `/admin/users`, `/admin/products`, `/admin/products/images`, `/admin/subscriptions`, etc. `public/routes.json` is the single 60-route registry consumed by the UI and copied into the build for `AdminPageController`. Each page keeps its own lazy module; the HTML bootstrap remains shared. Legacy `/admin-ui/index.html#/products` bookmarks are replaced with `/admin/products` without adding a history entry. Invitation/password-reset query parameters survive that replacement. Menu and related-page links support copy link, modified click and new tabs.

Spring serves the shell only for registered GET/HEAD page paths (including trailing slash); unknown `/admin/...` paths return 404, and POST is not a page request. Only this HTML shell is public; API authorization remains unchanged. Assets use absolute `/admin-ui/` URLs, so refresh works on nested routes. Vite development and preview use the same registry. Deployments must route `/admin` and `/admin/**` to Spring in addition to `/admin-ui/**`; proxies must not rewrite API errors or missing assets into HTML. This change has not been deployed.

Validation passed: `AdminPageControllerTest` exercises all 60 routes with and without trailing slash, HEAD, unknown paths, API isolation and POST rejection; `test:v2-navigation` checks route parity and path/legacy resolution; `test:v2-browser` covers real anchors/new tabs, refresh, history and permissions with fixture APIs; `test:page-serving` checks Vite dev/preview routing. Build and production tests pass. Current entry JS: 275,858 bytes; total JS: 1,447,583 bytes; CSS: 186,630 bytes. Production proxy and physical-device acceptance remain pending.

The 60 page routes now use 36 lazy page modules, with a central route/permission registry in `src/admin/navigation.tsx`. Sidebar groups cover overview, inbox, users, catalog, subscriptions/revenue, communications, AI, reports, system, and administration. Every route has one sidebar entry; page search shows only permitted destinations. Existing backend permissions and financial approval flows are unchanged.

The shell waits for the access profile before mounting a page. Forbidden deep links fall back to the first permitted page; failed access checks can be retried. Page loading and render failures remain inside the content area so navigation stays available. The mobile drawer supports Escape, focus containment, focus return, and inert background/closed-menu content. Back/forward navigation clears stale target context.

TR/EN currently covers the shell, all route names/descriptions, navigation search, language/theme controls, page loading/failure messages and contextual help. The language preference persists; changing it does not remount an open page or reset its form. Detailed page content, auth/session messages and shared table/form text still need incremental translation. This is the bilingual foundation, not completion of all admin localization.

Validation: `npm run build`, `npm run test:production` (includes `test:v2-navigation`), `npm run test:free-promotion-policy`, and `npm run test:v2-browser`. The browser test serves only built static files and intercepts every API call with fixtures; it never contacts a running backend. It exercises TR/EN persistence and filter retention, browser history, 390/768/1440px layouts, mobile keyboard behavior, forbidden deep links, profile failure/retry, deferred chunks and failed-chunk recovery. It requires Playwright and a browser; when these are outside the project, supply `PLAYWRIGHT_MODULE` as an absolute module import URL and `BROWSER_EXECUTABLE` as the browser executable path. Optional `V2_SCREENSHOT_DIR` saves desktop/mobile screenshots to an existing directory.

Measured build: entry JS 267,316 bytes (previously 703,560, about 62% smaller), total JS 1,393,225 bytes, entry CSS 181,533 bytes. Entry budget is now 300 KB. The deferred ECharts chunk remains about 606 KB and still triggers Vite's size warning. All 55 routes have registry/permission tests; browser fixtures cover representative shell flows, not every domain operation or physical device. Live backend/device acceptance remains open. No deployment or service restart was performed.

Next roadmap work includes the remaining Phase 3 context handoffs, detailed page localization/layouts, unified work inbox, daily summaries and H2/H3 observability. See `docs/ADMIN_UI_V2_MASTER_PLAN.md` in the repository root for the full plan.

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

### Session V2 compatibility

The admin UI now reads server deadlines from `adminSession` in admin auth responses.
`GET /api/v1/auth/admin/session` restores/rotates the bearer without extending idle time;
`POST /api/v1/auth/admin/refresh` acknowledges recent human activity. Normal API traffic
does not extend the server session. The HttpOnly session cookie and absolute expiry remain enforced.

Release the matching backend and built admin assets together, and reload existing admin
tabs. Old tabs do not implement the full activity contract and may time out while editing.
This is a release instruction, not evidence of deployment. Mobile user authentication is unchanged.

Run `npm run test:admin-security-session` for deadline, activity, cross-tab and transport
behavior coverage. Real browser/device sleep and reconnect checks remain part of release QA.

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
| Medium | Temporary single-page budgets: entry/per-chunk JS 750 KB, total JS 1.5 MB, entry CSS 200 KB (uncompressed decimal bytes). Revisit during the separate-page migration; retain performance checks. | Frontend | Performance phase |

These dependencies are explicit release risks, not silent TODOs. Owners must update severity and target when scope changes.
