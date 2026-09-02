# Notification Platform Implementation Record — 2026-08-31

## Scope and safety boundary

This work introduces the shared notification foundation, connects verified RevenueCat subscription lifecycle events to it, and prepares water, step, and basic fasting reminder producers for a separately gated migration. Meal, AI-result, and campaign producers remain unchanged. The shared dispatcher and each newly prepared behavior producer have independent deployment kill switches that are `false` by default in code, example configuration, and production configuration.

No migration was applied, no provider call was made, no real notification was created, and no AWS/build/deployment setting was activated.

## Sprint 1 — Typed notification contract and policy

Status: **COMPLETED LOCALLY**

- Added explicit classifications: transactional account, user-requested result, behavior reminder, marketing, and internal operational.
- Added typed event and delivery-channel contracts, including the subscription lifecycle and all five meal reminder definitions.
- Added allow-listed and required template parameter contracts. Unknown or missing required parameters fail before a database write.
- Added the central user-policy evaluator for global push, category preferences, marketing opt-out, quiet hours, disabled accounts, timezone validation, and defer/suppress reason codes.
- Kept account notifications in the in-app inbox when push is disabled. Marketing remains opt-in. Behavior reminders fail closed on invalid preference/time data.
- Added protected TR/EN subscription start, renewal, cancellation, resume, billing issue, expiry, plan change, pause, refund, and AI add-on definitions.
- Ordinary renewal defaults to in-app only. The other lifecycle definitions default to in-app and push, but shared delivery remains globally OFF.
- Blocked the legacy definition update endpoint from bypassing the future maker-checker workflow for protected subscription lifecycle copy.

## Sprint 2 — Shared occurrence, outbox, and attempt infrastructure

Status: **COMPLETED LOCALLY**

- Added `notification_occurrences`, `notification_outbox`, and `notification_delivery_attempts` persistence contracts in Flyway V240.
- Added provider-event idempotency on `(source, source_event_id, definition_key, user_id)` and per-channel outbox uniqueness.
- Added bounded PostgreSQL `FOR UPDATE SKIP LOCKED` claim semantics and expiring worker leases.
- Added transactional inbox + occurrence + outbox reservation with a per-user lock to serialize competing events.
- Added dispatch-time policy and definition revalidation, quiet-hour deferral, valid-token filtering, invalid-token revocation, TTL enforcement, bounded retry, and kill-switch cancellation.
- Added explicit provider accepted, delivered, retryable failure, final failure, invalid token, and unknown states.
- Provider timeout/connection loss is recorded as `UNKNOWN`; it is not blindly retried.
- Provider I/O occurs outside the reservation/state transactions.
- Email is represented as a future delivery channel but deliberately not activated by the Sprint 2 orchestrator.

## Sprint 3 — RevenueCat lifecycle integration

Status: **COMPLETED LOCALLY**

- Connected verified RevenueCat initial purchase, renewal, cancellation, renewal resume, billing issue, expiry, subscription refund, and one-off AI credit purchase events to the shared orchestrator.
- Subscription state mutation, provider-event audit, occurrence, in-app notification, and push outbox reservation now share the webhook transaction. A lifecycle-notification failure leaves the provider event retryable instead of silently losing the account message.
- Reused the stable RevenueCat provider event id as the notification source id. Existing provider-event and occurrence constraints prevent webhook retries from producing a second entitlement allocation or a second notification.
- Added safe TR/EN lifecycle parameter generation for plan names, dates, and AI credit validity. Store transaction ids, prices, offer codes, customer identifiers, and raw provider data are not exposed in user copy or template parameters.
- `PRODUCT_CHANGE` and `SUBSCRIPTION_PAUSED` are notification-only lifecycle events: the planned change is recorded and communicated, but current entitlement state is not ended or upgraded early. The subsequent effective purchase/expiry event remains authoritative.
- `TRANSFER` and unknown provider events remain explicitly ignored and create no user notification because the webhook does not carry enough trusted account context to message either side safely.
- AI add-on refunds remain state-only for now; they do not reuse the subscription-refund message. A dedicated negative AI-credit adjustment event can be added when its product/account wording and provider semantics are defined.
- Missing paid-plan mappings or lifecycle dates fail closed and retain the provider event for audited retry rather than emitting misleading account information.
- Updated the protected pause copy to describe a scheduled future pause rather than implying access has already ended.

## Sprint 4 — Admin governance and operational visibility

Status: **COMPLETED LOCALLY**

- Added a versioned notification-platform policy with a safe initial state: admin delivery intent is OFF and emergency stop is ON.
- Effective delivery now requires four independent gates: deployment delivery enabled, push provider enabled, maker-checker-published admin intent, and no emergency stop. Admin intent cannot override deployment configuration.
- Added immediate, audited emergency stop. Reopening or enabling delivery requires a fresh maker-checker approval and optimistic policy version match.
- Added protected maker-checker publication for subscription lifecycle and AI add-on copy. Direct legacy definition updates remain blocked.
- Added typed TR/EN preview using allow-listed parameters only. Preview never loads a real user or provider payload.
- Added transactional occurrence/outbox reason ledger and aggregate occurrence, outbox, attempt, opened, clicked, and dismissed metrics.
- Added a finance-scoped Account Notifications admin workspace showing layered gates, policy requests, emergency stop, preview, metrics, and ledger.
- Added V242 governance and engagement persistence. Engagement records are unique per notification, user, and engagement type.

## Sprint 5 — Mobile typed subscription notifications

Status: **COMPLETED LOCALLY**

- Added an explicit mobile contract for all subscription lifecycle and AI add-on notification types.
- Added a distinct account/subscription presentation category in the notification inbox while preserving backend-localized title and message copy.
- Hard allow-listed subscription navigation: lifecycle events route only to `manage-subscription`; AI add-on events route only to `ai-credits`.
- Push opens require a valid notification id and matching authenticated account before routing. Free-form provider routes cannot redirect subscription events elsewhere.
- Added idempotent OPENED, CLICKED, and DISMISSED tracking for in-app lifecycle notifications and PUSH CLICKED tracking before push navigation.
- No new notification-permission prompt was added.

## Sprint 6 — Staged behavior-reminder producer migration

Status: **COMPLETED LOCALLY**

- Routed water, step, and basic fasting reminder creation through one behavior-reminder boundary; the domain services no longer write notifications or call a push provider directly.
- Added separate `water`, `step`, and `basic-fasting` producer migration flags. Every flag defaults to `false` in normal, example, and production configuration, so the existing production path remains active until an explicit environment cutover.
- When a producer flag is enabled, the reminder uses the shared occurrence/inbox/outbox path with typed event definitions, timezone-aware eligibility and expiry, and stable cross-instance occurrence keys.
- Water quick-add keeps its explicit `250 ml` action payload. Step and fasting keep their allow-listed routes and actions.
- Preserved the legacy step eligibility contract while its flag is off. After cutover, preference rejection moves to the shared policy evaluator so suppression reasons are recorded centrally.
- Basic fasting occurrences are unique per fasting session and expire at the target end. Stale sessions fail before an occurrence is written.
- Reused the Sprint 2/V240 event definitions and persistence contracts; Sprint 6 requires no additional database migration.
- No mobile permission flow or mobile routing change was required for these already-supported reminder types.

## Sprint 7 — Release gates, cohort safety, and operational handoff

Status: **LOCAL IMPLEMENTATION COMPLETED / EXTERNAL ACCEPTANCE PENDING**

- Added deployment-owned `OFF`, `DRY_RUN`, `TEST_ACCOUNTS`, `PILOT`, and `LIVE` stages. Admin policy cannot advance or bypass the deployment stage.
- Added named internal test and pilot cohorts using numeric user IDs only. Empty/invalid cohorts fail closed; PILOT does not expose a percentage audience.
- Added deterministic LIVE cohorts controlled by `live-percentage`, supporting the required 5% → 25% → 100% progression without reshuffling users after a restart.
- Applied the release decision both when reserving an outbox and immediately before dispatch. A user outside the active cohort keeps an eligible transactional in-app message but receives no push outbox, preventing a later stage from accidentally sending that newly suppressed push.
- DRY_RUN performs no shared outbox claim and no provider I/O. Deployment OFF remains an independent master kill switch.
- Extended the admin Account Notifications workspace with the effective release stage, cohort counts, LIVE percentage, and water/step/basic-fasting producer migration state. Raw cohort IDs are not returned to the browser.
- Kept maker-checker publication, emergency stop, provider switch, user preferences, quiet hours, expiry, and producer flags as independent required gates.
- Added a release runbook covering evidence ownership, stage-by-stage validation, one-producer/one-percentage advancement, critical stop conditions, provider-accepted rollback limits, and outstanding external acceptance.
- No database migration was needed. Runtime, example, and production configuration all default to deployment disabled, stage OFF, LIVE 0%, empty cohorts, and all three producer migrations false.

## Verification executed

- `mvnw -DskipTests compile` with JDK 17: **BUILD SUCCESS**.
- Targeted suite: **22 tests, 0 failures, 0 errors, 0 skipped**.
- Covered policy classification/preferences/quiet hours, typed parameter validation, duplicate event behavior, missing-definition suppression, migration/kill-switch defaults, protected admin definition bypass, bounded retry, final failure, and `UNKNOWN` no-blind-retry behavior.
- Follow-up migration compatibility suite: **5 tests, 0 failures, 0 errors, 0 skipped**; the completed meal-reminder V230–V234 range remains contiguous while later product migrations are allowed.
- Sprint 3 focused RevenueCat suite: **28 tests, 0 failures, 0 errors, 0 skipped**.
- Sprint 3 subscription/orchestration regression suite: **95 tests, 0 failures, 0 errors, 0 skipped**.
- Sprint 3 migration and RevenueCat configuration contracts: **11 tests, 0 failures, 0 errors, 0 skipped**.
- Sprint 4–5 focused governance, notification, RevenueCat, subscription, and migration regression: **121 tests, 0 failures, 0 errors, 0 skipped**.
- Sprint 4 admin production build: **SUCCESS** (`tsc -b` and Vite production build).
- Sprint 5 mobile TypeScript check: **SUCCESS** (`tsc --noEmit`).
- Sprint 5 subscription notification contract: **PASSED**.
- Sprint 6 behavior reminder and full notification regression: **109 tests, 0 failures, 0 errors, 0 skipped**.
- Sprint 6 Spring application-context/controller verification: **4 tests, 0 failures, 0 errors, 0 skipped**. The pre-existing H2-only `ON CONFLICT` scheduler warning was logged asynchronously; it did not fail the test and is unrelated to the behavior producer migration.
- Sprint 7 release-gate focused suite: **24 tests, 0 failures, 0 errors, 0 skipped**.
- Sprint 7 notification-platform regression: **119 tests, 0 failures, 0 errors, 0 skipped**.
- Sprint 7 Spring application-context/controller verification: **4 tests, 0 failures, 0 errors, 0 skipped**. The same pre-existing asynchronous H2-only meal scheduler warning remains unrelated.
- Sprint 7 admin production build: **SUCCESS** (`tsc -b` and Vite production build; existing bundle-size warning only).

## Remaining work

- Later migration: move meal, AI-result, campaign, and any advanced fasting producers to the shared platform incrementally; do not switch the meal reminder implementation until parity and migration safety are demonstrated.
- External gates: apply V240 and V242 to fresh and representative PostgreSQL environments, run two-instance claim tests, provider receipt tests, signed-in admin checks, physical-device push/deep-link checks, and staged OFF/DRY/PILOT/LIVE release evidence.

Current release state: **OFF / NOT DEPLOYED / NOT READY FOR LIVE DELIVERY**.
