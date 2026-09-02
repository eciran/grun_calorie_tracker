# Meal Reminder Release Runbook — 29 August 2026

## Current verdict

**OFF / NOT READY FOR PILOT OR LIVE.** Local release preparation is complete. No migration was applied to an external database, no real push was sent, and no production/build setting was changed.

Safe code defaults are `grun.meal-reminders.delivery-enabled=false`, meal-reminder mode `OFF`, general push `enabled=false`, push provider `LOG`, candidate/outbox batches `100`, leases `2m`, retry base `2m`, max attempts `3`, and deterministic jitter `45s`. An admin policy cannot override the deployment kill switch or general push gate.

## Owners and approval boundary

- Release owner: **UNASSIGNED — WAITING**
- Independent checker: **UNASSIGNED — WAITING**
- Mobile/device verifier: **UNASSIGNED — WAITING**
- Operations observer: **UNASSIGNED — WAITING**

Named people must be recorded before DRY_RUN begins. Maker and checker must be different authorized admins. This document does not grant access or approval.

## Migration and local contract

The current branch contains one contiguous reminder series with no duplicate version: V230 snapshot indexes, V231 dry-run decisions, V232 reliable delivery, V233 admin policy and V234 interactions. Static validation asserts that V234 is the current maximum migration. Actual Flyway validation against both a fresh PostgreSQL database and a representative existing database is **WAITING**.

The complete A01–A28 evidence index is in `MEAL_REMINDER_ACCEPTANCE_MATRIX_2026-08-29.md`. Local evidence includes bounded batch queries, no-unbounded-candidate guard, per-candidate and per-outbox failure isolation, expired-lease recovery, provider-timeout UNKNOWN handling, kill switch, preferences, quiet hours, fasting, kcal reliability, idempotency, admin security, and mobile routing.

## Required release sequence

1. Keep deployment and push gates OFF. Assign named owners and capture current queue/receipt/opt-out baselines.
2. Validate Flyway on fresh and representative existing PostgreSQL databases. Run the concurrency and representative-volume suites against PostgreSQL; scheduler p95 must remain below the scan interval.
3. In DRY_RUN, inspect all breakfast, lunch, dinner/kcal and daily catch-up slots in TR and EN. Confirm reasons match admin preview and no delivery attempts exist.
4. With maker-checker approval, use PILOT only for named internal accounts. Capture development/internal build evidence for foreground, background, cold start, signed-out then login, account switch, old-client payload, multi-device token mix, preference opt-out and a real provider receipt.
5. Observe PILOT for at least two complete local days with an adequate sample. Any critical stop below returns the system to OFF.
6. With fresh approval at every step, stage LIVE at 5%, 25%, then 100%. Observe at least two complete local days and an adequate sample at each stage. Never advance on elapsed time alone.

## Alarm thresholds and stop rules

These thresholds are release gates, not promises that delivery is healthy below them.

- Any wrong kcal, preference violation, quiet-hours/fasting violation, cross-account route, or duplicate occurrence: **critical; emergency stop immediately**.
- Provider final failure or receipt failure: warn above 2% over 15 minutes; **stop at or above 5%** over 15 minutes, with at least 20 accepted attempts before percentage-only decisions.
- Queue lag p95: warn above 2 minutes; **stop above 5 minutes**.
- Scheduler/outbox task duration p95: warn above 3 seconds; **stop at or above the 5-second worker scan interval**.
- Reminder opt-out rate: warn at +2 percentage points over the captured 7-day baseline; **stop at +5 points** over a rolling 24-hour window.
- UNKNOWN/provider-uncertain outcomes: investigate every occurrence; never blind-retry an attempt that may have reached the provider.

Admin metrics report correlation only: a meal log within two hours of an idempotent open is not proof that the reminder caused the log.

## Failure drills required before PILOT

- Preference OFF after queue reservation: revalidation cancels delivery.
- Deployment kill switch OFF during processing: no new claims and pending work becomes ineligible.
- Provider timeout after request write: record UNKNOWN/uncertain once; do not blind-retry.
- One corrupt candidate/outbox: record/log its failure and continue the bounded page without a bulk rollback.
- Worker crash or database restart: wait for lease expiry, reclaim once, and suppress expired slots.
- Two workers: prove `FOR UPDATE SKIP LOCKED` and unique occurrence/attempt constraints on actual PostgreSQL.

The deterministic local tests cover the logic above. Actual database restart and multi-instance drills are **WAITING**.

## Emergency rollback

1. Close `grun.meal-reminders.delivery-enabled` first and verify no new claims/provider calls.
2. Use the audited admin emergency stop; verify pending rows fail revalidation and expired slots do not replay.
3. If a policy caused the incident, request rollback to the last approved policy through maker-checker. Approval never backfills an old occurrence.
4. Do not reverse V230–V234 as an incident response. Preserve audit/outbox/attempt/interaction evidence for diagnosis and retention processing.
5. A request already accepted by the provider cannot be retracted. Mark ambiguous outcomes UNKNOWN and reconcile by receipt; never create a replacement attempt blindly.

## Evidence still WAITING

- Fresh and representative-existing PostgreSQL Flyway validation, concurrency, restart and representative-volume run.
- Signed-in admin maker-checker walkthrough and alarm/dashboard observation.
- Development/internal build on physical devices, including cold start, account switch and multi-device behavior.
- Real provider receipt and invalid-token reconciliation.
- Full-day DRY_RUN, two-day PILOT, and two-day observations at each 5%/25%/100% LIVE stage.
- Named release owner, independent checker, device verifier and operations observer.

Until every applicable item is evidenced and approved, the only allowed state is OFF.
