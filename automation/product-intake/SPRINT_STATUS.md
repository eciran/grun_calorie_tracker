# Product Intake Automation Status

**Branch:** `feature/unified-product-intake-review`  
**Source plan:** `docs/UNIFIED_PRODUCT_INTAKE_AND_CATALOG_REVIEW_MASTER_PLAN_2026-07-29.md`  
**Cadence:** 30 minutes  
**Per-run implementation budget:** 20 minutes  
**Program status:** READY  
**Active sprint:** Sprint 0  
**Last completed run:** Never

## Sprint board

| Sprint | State | Exit summary |
|---|---|---|
| Sprint 0 — Baseline, contract and feasibility | ACTIVE | Establish safe implementation baseline and contracts. |
| Sprint 1 — Publication gate | PENDING | Internal candidates cannot leak to user reads. |
| Sprint 2 — Common Review Case | PENDING | User/admin/correction sources share one case service. |
| Sprint 3 — Direct storage and retention | PENDING | Private two-asset upload and bounded deletion work. |
| Sprint 4 — Admin intake and assignments | PENDING | Product Intake queue and real food assignment work. |
| Sprint 5 — Mobile user flow | PENDING_EXTERNAL_REPO | Requires explicitly approved frontend-repository work. |
| Sprint 6 — Apply/publish hardening | PENDING | Evidence-backed apply and publish are atomic. |
| Sprint 7 — TR pilot closure | PENDING | Rollout, metrics, cost and rollback gates pass. |

## Sprint 0 work queue

The automation must execute these in order unless the preceding item records a
safe dependency reason for parallel progress.

- [ ] `S0-01` Record the backend/admin baseline commands and focused tests for
  contribution, product review, search, permissions and assignment.
- [ ] `S0-02` Produce a code-backed inventory of every user-facing FoodItem read
  path that must enforce publication status.
- [ ] `S0-03` Add the versioned Review Case API/state contract without persistence
  changes.
- [ ] `S0-04` Add publication-gate characterization tests that describe current
  leakage behavior before implementation.
- [ ] `S0-05` Define the provider-neutral direct-upload/storage interface and
  configuration contract without real credentials or external mutations.
- [ ] `S0-06` Produce the mobile OCR/backend handoff contract and record the
  separate frontend dependency.
- [ ] `S0-07` Run the Sprint 0 focused regression set and publish the Sprint 1
  entry checklist.

## Active slice

**ID:** None  
**State:** NOT_STARTED  
**Owner/run ID:** None  
**Started:** None  
**Expected files:** None  
**Required verification:** None

## Blockers

None.

## Run log

| Time (Europe/Dublin) | Run ID | Slice | Result | Verification | Commit |
|---|---|---|---|---|---|
| — | — | — | Automation initialized | — | — |

## Tracker update rules

- Set the active slice before editing implementation files.
- Keep an incomplete slice active when a focused test fails.
- Record exact commands and pass/fail counts; do not write only “tests passed”.
- A blocked item must name the missing authority, credential, repository or
  product decision.
- Do not mark a sprint complete while a P0 acceptance item is skipped.
