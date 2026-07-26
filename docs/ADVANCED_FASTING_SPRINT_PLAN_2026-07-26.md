# Advanced Fasting Sprint Plan

Date: 2026-07-26
Status: Approved for implementation planning

## Goal

Extend the existing basic fasting timer, history, summary and reminder flow with a safe weekly-program model, 5:2 support, advanced analytics and controlled subscription access without breaking existing basic clients.

This module is a wellness tracker, not a medical diagnosis or treatment system. Production release still requires final clinical, legal, privacy and store-policy review for the target markets.

## Product Boundary

### Basic fasting

- Daily preset plans and a controlled custom daily window.
- Start, finish and cancel a fasting session.
- Active timer, history, daily summary and basic range summary.
- Basic streak and one target-end reminder.

### Advanced fasting

- Different fasting rules for each weekday.
- Rest days, plan pause/resume and effective-date changes.
- 5:2 reduced-calorie-day scheduling and adherence tracking.
- Advanced reminders and weekly calendar.
- Planned-versus-actual adherence, consistency and early-stop analysis.
- Cross-domain analytics through the separate `ADVANCED_ANALYTICS` entitlement.

Recommended commercial mapping:

- FREE: `FASTING_BASIC`
- PLUS: `FASTING_BASIC` and `FASTING_ADVANCED`
- PRO: PLUS rights and cross-domain `ADVANCED_ANALYTICS`

## Non-Negotiable Safety Rules

- No fasting program is presented as medical advice or a guaranteed health result.
- Advanced eligibility is limited to adults.
- Pregnancy or breastfeeding, eating-disorder risk/history, diabetes or glucose-affecting medication and other declared risk conditions require a hard block or clinician-directed flow according to the approved safety policy.
- Continuous fasting longer than 24 hours is outside the MVP.
- Existing 48-hour request and database limits must be replaced by policy-backed validation and a safe migration strategy for existing plans.
- Longer daily windows require explicit, versioned acknowledgement.
- A user can stop a session at any time and optionally record a structured reason.
- Safety restrictions cannot be weakened beyond the coded maximum from admin UI.
- Health declarations are minimized, access-controlled, audited and included in GDPR export/delete/retention handling.
- Analytics describe recorded associations only; they must not claim causation.
- 5:2 is represented as two non-consecutive reduced-calorie days, not as a continuous two-day zero-calorie fasting session.

## Sprint 1 - Safety Policy and Entitlement Boundary

- Add centralized, versioned fasting safety policy and eligibility/acknowledgement contracts.
- Replace generic 48-hour validation with plan-type and policy-aware validation.
- Route advanced endpoints to `FASTING_ADVANCED` before general `FASTING_BASIC` matching.
- Add stable eligibility, unsafe-duration and acknowledgement error codes.
- Preserve current basic API contracts.
- Gate: backend rejection, entitlement, controller and filter tests pass.
- Commit: `feat(fasting): add safety policy and advanced entitlement boundary`

## Sprint 2 - Weekly Program and Versioned Data Model

- Add weekly programs and `FAST`, `REDUCED_CALORIE`, `NORMAL`, `REST` day rules.
- Add `DRAFT`, `ACTIVE`, `PAUSED`, `ARCHIVED` lifecycle and effective dates.
- Keep immutable program versions and planned-rule session snapshots.
- Add constraints, indexes, optimistic locking and GDPR handling.
- Gate: clean/existing PostgreSQL Flyway and repository integration tests pass.
- Commit: `feat(fasting): add versioned weekly program model`

## Sprint 3 - Program API and Preview

- Add create/read/update/preview/activate/pause/archive APIs.
- Validate two non-consecutive reduced-calorie days for 5:2.
- Return a timezone-aware seven-day preview.
- Make activation owner-scoped, concurrency-safe and idempotent.
- Gate: overlap, DST, ownership, idempotency and OpenAPI tests pass.
- Commit: `feat(fasting): add advanced weekly program API`

## Sprint 4 - Session and 5:2 Execution

- Connect daily occurrences to existing sessions.
- Record planned versus actual times and structured finish/stop/skip reasons.
- Evaluate reduced-calorie-day adherence from canonical food-log totals.
- Recalculate affected days safely after late food-log edits.
- Never create fake 48-hour sessions for 5:2.
- Gate: backward compatibility, transaction, ownership and integration tests pass.
- Commit: `feat(fasting): execute weekly and five-two program rules`

## Sprint 5 - Smart Reminder Flow

- Add pre-start, start, nearing-completion, completion and missed-plan reminders.
- Respect account/module preferences, timezone and quiet hours.
- Prevent duplicates with stable occurrence keys and connect to the provider-independent push layer.
- Use neutral, non-judgmental copy.
- Gate: retry, duplicate, disabled preference and quiet-hour tests pass.
- Commit: `feat(fasting): add safe advanced reminder scheduling`

## Sprint 6 - Advanced Analytics

- Add adherence, schedule consistency, average duration and early-stop analytics.
- Add weekday trends, stop-reason distribution and 5:2 adherence.
- Return minimum-data and data-quality indicators; never convert missing data to zero.
- Gate cross-domain weight/nutrition associations with `ADVANCED_ANALYTICS`.
- Gate: entitlement, partial-data, range-limit and query-performance tests pass.
- Commit: `feat(fasting): add advanced adherence analytics`

## Sprint 7 - Admin Governance and Monitoring

- Expose active policy version and non-sensitive operational metrics.
- Audit policy, entitlement and support configuration changes.
- Monitor blocked unsafe requests, scheduler failures and push failures.
- Permit operational configuration only inside coded safety bounds.
- Gate: no sensitive declarations in logs/list views; admin authorization and audit tests pass.
- Commit: `feat(admin): add advanced fasting governance`

## Sprint 8 - Release Hardening and Mobile Handoff

- Finish localization-ready errors, API contract and consolidated UI handoff.
- Document consent, retention, privacy, accessibility and safety-copy acceptance.
- Run targeted, PostgreSQL Flyway, full backend and mobile contract tests.
- Record real-device/push/provider and clinical/legal acceptance as external release gates.
- Gate: no migration conflict, green full suite and backward-compatible basic clients.
- Commit: `docs(fasting): finalize advanced fasting release contract`

## Commit Discipline

- Each sprint receives one focused commit only after its acceptance checks pass.
- Unrelated working-tree files are never staged with fasting changes.
- A failed sprint is not committed as complete.
- Progress log and TODO status are updated in the same sprint commit.
- Push is performed only when explicitly requested.