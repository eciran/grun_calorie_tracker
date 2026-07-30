# Product Intake Automation Status

**Branch:** `feature/unified-product-intake-review`  
**Source plan:** `docs/UNIFIED_PRODUCT_INTAKE_AND_CATALOG_REVIEW_MASTER_PLAN_2026-07-29.md`  
**Cadence:** 30 minutes  
**Per-run implementation budget:** 20 minutes  
**Program status:** IN_PROGRESS
**Active sprint:** Sprint 4
**Last completed run:** 2026-07-30 — Sprint 2 closure verified

## Sprint board

| Sprint | State | Exit summary |
|---|---|---|
| Sprint 0 — Baseline, contract and feasibility | DONE | Baseline and implementation contracts established. |
| Sprint 1 — Publication gate | DONE | User reads are guarded and publication is centralized. |
| Sprint 2 — Common Review Case | DONE | User/admin/correction sources share the review-case foundation and legacy bridge. |
| Sprint 3 — Direct storage and retention | ACTIVE | Private two-asset upload and bounded deletion work. |
| Sprint 4 — Admin intake and assignments | PENDING | Product Intake queue and real food assignment work. |
| Sprint 5 — Mobile user flow | PENDING_EXTERNAL_REPO | Requires explicitly approved frontend-repository work. |
| Sprint 6 — Apply/publish hardening | PENDING | Evidence-backed apply and publish are atomic. |
| Sprint 7 — First-market pilot and global operations closure | PENDING | Market-configured rollout, portability, metrics, cost and rollback gates pass. |

## Sprint 3 work queue

The automation must execute these in order unless the preceding item records a
safe dependency reason for parallel progress.

- [x] `S3-01` Finalize the provider-neutral S3-compatible storage properties,
  client and presigner contracts without embedding a market or provider.
  Verified 2026-07-30 with `FoodContributionS3ConfigTest` (4 tests).
- [x] `S3-02` Implement private FRONT_PACKAGE and NUTRITION_LABEL direct-upload
  slots with opaque keys and bounded expiry.
  Verified 2026-07-30 with the focused Sprint 3 suite (8 tests).
- [x] `S3-03` Validate checksum, MIME signature, decoded dimensions and upload
  session state during finalize.
  Verified 2026-07-30 with the focused Sprint 3 suite (16 tests).
- [x] `S3-04` Add permission-scoped, short-lived admin evidence reads.
  Verified 2026-07-30 with the focused Sprint 3 suite (21 tests).
- [x] `S3-05` Implement idempotent retention cleanup, reconciliation,
  withdrawal purge and account export/delete integration.
- [x] `S3-06` Add upload, finalize, cleanup and authorization rate limits,
  metrics and focused tests.
- [x] `S3-07` Publish provider lifecycle/CORS configuration and operational
  verification evidence.
## Sprint 4 work queue

- [ ] `S4-01` Add admin Product Intake queue DTOs, filters and paginated queries.
- [ ] `S4-02` Add active ADMIN_CATALOG assignment validation and claim/release/reassign actions.
- [ ] `S4-03` Add request-better-evidence, evidence approve/reject and existing-product attach actions.
- [ ] `S4-04` Add admin manual-product intake as an internal candidate with action-level permission gates.
- [ ] `S4-05` Add Workbench submission detail contract with lazy evidence, field comparison, risk/warnings and expiry.
- [ ] `S4-06` Complete backend/admin-portal acceptance verification and Sprint 4 handoff.

## Active slice

**ID:** S4-01
**State:** READY
**Owner/run ID:** unassigned
**Started:** 2026-07-30
**Expected files:** Admin Product Intake queue DTOs, repository queries, service/controller and focused tests
**Required verification:** My queue, unassigned, needs-action, high-risk and overdue filters paginate without exposing private evidence URLs
## Blockers

None for Sprint 4 implementation.

## Deferred release gates

- `S3-PROVIDER-PROOF` (user-deferred 2026-07-30): before staging/production release, select the S3-compatible provider and private bucket, replace example origins, apply CORS/lifecycle policies with approved credentials, and archive provider-side privacy/30-day/90-day/rollback evidence. This gate must be surfaced again before Sprint 7 closure or any deployment.

## Run log

| Time (Europe/Dublin) | Run ID | Slice | Result | Verification | Commit |
|---|---|---|---|---|---|
| 2026-07-30 | manual-sprint-1 | Sprint 1 | DONE | Publication visibility and guarded publish implementation | `7e361f3`, `dd6a1c4` |
| 2026-07-30 | manual-sprint-2 | Sprint 2 | DONE | Common review cases and legacy bridge implementation | `a243d1b` |
| 2026-07-30 | manual-sprint-3-s3-01 | S3-01 | DONE | `FoodContributionS3ConfigTest`: 4 passed, 0 failed | Uncommitted Sprint 3 |
| 2026-07-30 | manual-sprint-3-s3-02 | S3-02 | DONE | Focused Sprint 3 suite: 8 passed, 0 failed | Uncommitted Sprint 3 |
| 2026-07-30 | heartbeat-20260730T102015Z | S3-03 | DONE | Focused Sprint 3 suite: 16 passed, 0 failed | Uncommitted Sprint 3 |
| 2026-07-30 | heartbeat-20260730T104015Z | S3-04 | DONE | Focused Sprint 3 suite: 21 passed, 0 failed | Uncommitted Sprint 3 |
| 2026-07-30 | manual-sprint-3-s3-05-retention-core | S3-05 | IN_PROGRESS | Retention/GDPR deletion core: 10 passed, 0 failed; export metadata remains | Uncommitted Sprint 3 |
| 2026-07-30 | heartbeat-20260730T110016Z | S3-05 | DONE | GDPR export now includes review-case/evidence metadata while excluding storage keys and signed URLs; focused retention/GDPR/review suite: 10 passed, 0 failed | Uncommitted Sprint 3 |
| 2026-07-30 | manual-20260730-s3-06 | S3-06 | DONE | Dedicated upload/finalize/evidence-read limits plus operation/cleanup metrics; focused suites: 35 passed, 0 failed | Uncommitted Sprint 3 |
| 2026-07-30 | heartbeat-20260730T112016Z | S3-07 | IN_PROGRESS | Provider-neutral CORS/lifecycle templates, validator and rollback runbook added; offline policy VALID (30/90/1 days). External provider evidence blocked on approved provider/bucket/origins/credentials | Uncommitted Sprint 3 |
| 2026-07-30 | manual-20260730-close-s3-defer-provider | Sprint 3 | DONE_WITH_RELEASE_GATE | User deferred external provider proof; complete local Sprint 3 suite: 51 passed, 0 failed; policy validator VALID; S3-PROVIDER-PROOF retained for pre-release/Sprint 7 | Pending commit |

## Tracker update rules

- Set the active slice before editing implementation files.
- Keep an incomplete slice active when a focused test fails.
- Record exact commands and pass/fail counts; do not write only “tests passed”.
- A blocked item must name the missing authority, credential, repository or
  product decision.
- Do not mark a sprint complete while a P0 acceptance item is skipped.
