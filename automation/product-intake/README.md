# Product Intake Sprint Automation

This directory is the execution contract for the recurring implementation work
defined in:

`docs/UNIFIED_PRODUCT_INTAKE_AND_CATALOG_REVIEW_MASTER_PLAN_2026-07-29.md`

## Branch boundary

Automation may run only when the checked-out branch is:

`feature/unified-product-intake-review`

It must stop without changing files when:

- another branch is checked out;
- an active automation lock exists;
- the working tree contains changes that are not explained by the current
  tracker entry;
- a task requires credentials, production access, deployment, destructive data
  changes or a product decision not already made in the master plan.

## Cadence and work budget

- Trigger cadence: every 30 minutes.
- Maximum intended implementation window per run: 20 minutes.
- One run completes at most one cohesive, reviewable work slice.
- A run may resume the previous incomplete slice, but must not start a second
  slice in the same execution.

The short work budget prevents overlapping runs and keeps commits small enough
to review or revert.

## Required run protocol

1. Verify the exact branch and inspect `git status --short`.
2. Generate a unique run ID and acquire the repository lock with
   `scripts/product-intake-automation-lock.ps1`.
3. Read the master plan and `SPRINT_STATUS.md`.
4. Select the next unchecked item in the active sprint.
5. Inspect the relevant implementation and tests before editing.
6. Implement only the smallest complete slice.
7. Run focused tests proportional to the change.
8. Update `SPRINT_STATUS.md` with result, evidence and next action.
9. Commit only the slice files with an explicit path list when tests pass.
10. Never push, merge, deploy or provision external services.
11. Release the lock in a `finally` path.

## Git rules

- Do not use `git reset --hard`, destructive checkout or broad cleanup.
- Do not stash or overwrite unexplained user changes.
- Do not edit applied Flyway migrations. Always resolve the next available
  version at execution time.
- Do not include unrelated generated files.
- Commit message format:

  `feat(product-intake): <bounded result>`

- If tests fail, keep the work uncommitted, record the exact failure in the
  tracker and let the next run resume it.

## Safety rules

- No AWS, R2 or production mutations from recurring runs.
- No secret creation, rotation or output.
- No dependency download unless it is already authorized and required by the
  active sprint.
- No automatic public catalog publication.
- No weakening of evidence, permission, publication or retention gates.
- No implementation in the separate mobile repository from this automation.
  Mobile work is recorded as a handoff until a dedicated frontend automation is
  explicitly approved.

## Completion behavior

When all acceptance items in a sprint pass:

1. Mark the sprint `DONE`.
2. Advance exactly one sprint in `SPRINT_STATUS.md`.
3. Do not skip blocked security or migration gates.

When Sprint 7 is complete, mark the tracker `PROGRAM_COMPLETE`. Subsequent
automation runs must become read-only and report that no work remains.
