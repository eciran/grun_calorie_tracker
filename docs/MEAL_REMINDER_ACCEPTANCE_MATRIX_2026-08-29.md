# Meal Reminder Acceptance Matrix — 29 August 2026

This is the MR-08 release evidence index. `LOCAL PASS` means automated, deterministic evidence exists in this workspace. It is not evidence of a deployed environment, a physical device, or production traffic. The canonical scenario definitions remain in the sprint plan.

| ID | Local evidence | Status / remaining gate |
|---|---|---|
| A01 | `MealReminderDecisionEngineTest`, preference revalidation tests | LOCAL PASS |
| A02 | Decision fixtures: missing breakfast produces `DAILY_CATCHUP`, never dinner-specific copy | LOCAL PASS |
| A03 | Snapshot service: empty canonical food and recipe aggregates | LOCAL PASS |
| A04 | Decision engine: prior meals recorded, dinner missing, safe remaining-kcal branch | LOCAL PASS |
| A05 | Decision engine: completed day suppression | LOCAL PASS |
| A06 | `DailyMealReminderSnapshotServiceTest`: food and recipe aggregates are combined once | LOCAL PASS |
| A07 | Snapshot service: planned but unconsumed items do not count as logs | LOCAL PASS |
| A08 | Snapshot service: unresolved partial/replacement becomes UNKNOWN and never double-counts | LOCAL PASS |
| A09 | Contract classification: one skipped item is not a whole-meal exclusion | LOCAL PASS |
| A10 | Contract classification: explicit whole-meal exclusion | LOCAL PASS |
| A11 | Decision fixtures: invalid/missing effective goal suppresses kcal copy | LOCAL PASS |
| A12 | Reservation/dispatch revalidation tests: diary, goal, preference, token and policy are rechecked | LOCAL PASS |
| A13 | Engine fixtures: quiet hours and active fasting fail closed | LOCAL PASS |
| A14 | Contract/snapshot tests: IANA zones, DST 23/25-hour days and invalid zones | LOCAL PASS |
| A15 | SQL contracts require bounded `FOR UPDATE SKIP LOCKED`; concurrency integration test exists | LOCAL PASS; actual PostgreSQL multi-instance run WAITING |
| A16 | Expired lease/stale-slot SQL and state tests | LOCAL PASS; database restart drill WAITING |
| A17 | Per-token attempt uniqueness and account-bound interaction tests | LOCAL PASS; physical multi-device evidence WAITING |
| A18 | `MealReminderOutboxDispatcherTest`: timeout becomes UNKNOWN/uncertain with no blind retry | LOCAL PASS |
| A19 | Push token/receipt and dispatch-state tests cover missing, invalid and failed tokens | LOCAL PASS; real provider receipt WAITING |
| A20 | OFF/DRY_RUN tests verify no provider delivery and no send-state mutation | LOCAL PASS |
| A21 | Admin policy tests: rollback is versioned, audited and does not replay an occurrence | LOCAL PASS |
| A22 | Permission, audit, maker-checker and admin UI contract tests | LOCAL PASS; signed-in environment walkthrough WAITING |
| A23 | Reservation uniqueness: catch-up and dinner share one evening occurrence | LOCAL PASS |
| A24 | Runtime-state tests cover routine-notification cooldown | LOCAL PASS |
| A25 | Mobile routing contract covers old/unknown payload fallback and account isolation | LOCAL PASS; cold-start/internal-build device evidence WAITING |
| A26 | Admin preview and engine parity tests use the same decision contract | LOCAL PASS |
| A27 | Dispatch revalidation cancels queued work after preference opt-out | LOCAL PASS |
| A28 | Interaction uniqueness and conversion tests prevent duplicate open/log events | LOCAL PASS |

Release verdict: **NOT READY FOR PILOT/LIVE**. Local application and contract evidence is complete, but the explicitly marked PostgreSQL, signed-in admin, real-provider, physical-device, load and elapsed-observation gates have not been executed.
