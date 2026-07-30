# Product Intake Automation Status

**Branch:** `feature/unified-product-intake-review`  
**Source plan:** `docs/UNIFIED_PRODUCT_INTAKE_AND_CATALOG_REVIEW_MASTER_PLAN_2026-07-29.md`  
**Cadence:** 30 minutes  
**Per-run implementation budget:** 20 minutes  
**Program status:** IN_PROGRESS
**Active sprint:** Sprint 7
**Last completed run:** 2026-07-30 Ã¢â‚¬â€ Sprint 2 closure verified

## Sprint board

| Sprint | State | Exit summary |
|---|---|---|
| Sprint 0 Ã¢â‚¬â€ Baseline, contract and feasibility | DONE | Baseline and implementation contracts established. |
| Sprint 1 Ã¢â‚¬â€ Publication gate | DONE | User reads are guarded and publication is centralized. |
| Sprint 2 Ã¢â‚¬â€ Common Review Case | DONE | User/admin/correction sources share the review-case foundation and legacy bridge. |
| Sprint 3 Ã¢â‚¬â€ Direct storage and retention | DONE_WITH_RELEASE_GATE | Local implementation complete; provider proof deferred to pre-release/Sprint 7. |
| Sprint 4 Ã¢â‚¬â€ Admin intake and assignments | DONE | Unified queue, assignment, Workbench, evidence and manual intake acceptance passed. |
| Sprint 5 Ã¢â‚¬â€ Mobile user flow | DONE | User approved work on the existing mobile frontend branch; unrelated changes remain untouched. |
| Sprint 6 - Apply/publish hardening | DONE | Evidence-backed apply and publish are atomic. |
| Sprint 7 - First-market pilot and global operations closure | ACTIVE | Market-configured rollout, portability, metrics, cost and rollback gates pass. |

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

- [x] `S4-01` Add admin Product Intake queue DTOs, filters and paginated queries.
- [x] `S4-02` Add active ADMIN_CATALOG assignment validation and claim/release/reassign actions.
- [x] `S4-03` Add request-better-evidence, evidence approve/reject and existing-product attach actions.
- [x] `S4-04` Add admin manual-product intake as an internal candidate with action-level permission gates.
- [x] `S4-05` Add Workbench submission detail contract with lazy evidence, field comparison, risk/warnings and expiry.
- [x] `S4-06` Complete backend/admin-portal acceptance verification and Sprint 4 handoff.

## Active slice

**ID:** S7-04
**State:** BLOCKED_EXTERNAL_EVIDENCE
**Owner/run ID:** manual-20260730-s7-04
**Started:** 2026-07-30
**Expected files:** OCR thresholds, moderation SLA/capacity, retention and provider operational validation
**Required verification:** Pilot quality and operational gates have measurable thresholds and rehearsal evidence
## Blockers

- `S4-ADMIN-PORTAL`: RESOLVED 2026-07-30. The portal source is the backend
  repository's `admin-ui`, not the mobile frontend repository. The legacy
  Label Contributions route now renders the unified Product Intake Workbench.
- `S5-FRONTEND-SCOPE`: RESOLVED 2026-07-30. User approved Product Intake work
  on the existing `Claude_Grun_frontend` branch. The unrelated modified
  `docs/FRONTEND_UI_STANDARD_AUDIT.md` file is preserved and excluded.

## Sprint 5 work queue

- [x] `S5-01` Add the third barcode-not-found CTA, localized front/nutrition capture, device-side JPEG normalization and a manual fallback route.
- [x] `S5-02` Add OCR adapter/parser warnings, correction form and restart-safe draft storage.
- [x] `S5-03` Add idempotent direct upload/finalize with progress and retry.
- [x] `S5-04` Add explicit submission consent, optional Custom Food separation and submit.
- [x] `S5-05` Add My Contributions, request-evidence deep links and withdrawal.
- [x] `S5-06` Complete mobile accessibility, localization and acceptance verification.

## Sprint 6 work queue

- [x] `S6-01` Convert accepted case values into immutable reviewed source evidence.
- [x] `S6-02` Apply only explicitly selected fields to an existing published product.
- [x] `S6-03` Verify and publish a new internal candidate through the central publication service.
- [x] `S6-04` Make quality, canonical/search recalculation, cache invalidation and audit atomic.
- [x] `S6-05` Add high-impact confirmation, corroboration context and user decision notifications.
- [x] `S6-06` Complete concurrency, rollback and Sprint 6 acceptance verification.

## Sprint 7 work queue

- [x] `S7-01` Add market-neutral rollout flags, deterministic cohorts, safe defaults and kill-switch enforcement.
- [x] `S7-02` Enable internal dogfood with pilot observability and existing-flow fallbacks.
- [x] `S7-03` Add configurable 1/10/50/100 market rollout and prove a second market needs no code change.
- [ ] `S7-04` Validate OCR thresholds, moderation SLA/capacity, retention and provider operations.
- [ ] `S7-05` Complete cost/abuse/rollback rehearsal, release runbook and Sprint 7 acceptance.

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
| 2026-07-30 | manual-20260730-s4-01 | S4-01 | DONE | Six queue modes plus status/market filters paginate; summary contract excludes URL/storage/checksum fields; 7 passed, 0 failed | Uncommitted Sprint 4 |
| 2026-07-30 | heartbeat-20260730T114216Z | S4-02 | DONE | V203 assignment fields; locked claim/release/reassign; active ADMIN_CATALOG and owner-only reassignment; timestamp clears on release; 12 passed, 0 failed | Uncommitted Sprint 4 |
| 2026-07-30 | manual-20260730-s4-03 | S4-03 | DONE | Assigned-admin/owner request-evidence, approve/reject and attach-existing actions; user-visible notification; catalog publication untouched; 17 passed, 0 failed | Uncommitted Sprint 4 |
| 2026-07-30 | manual-20260730-s4-04 | S4-04 | DONE | OWNER/ADMIN_CATALOG manual intake uses ADMIN_MANUAL review command; service enforces INTERNAL_REVIEW candidate and blocks read-only/published results; 24 passed, 0 failed | Uncommitted Sprint 4 |
| 2026-07-30 | manual-20260730-s4-05 | S4-05 | DONE | Workbench detail parses submitted/catalog fields, compares values, reports risk/availability/expiry, and exposes URL-free lazy evidence descriptors; 22 passed, 0 failed | Uncommitted Sprint 4 |
| 2026-07-30 | heartbeat-20260730T120216Z | S4-06 | BLOCKED_EXTERNAL_REPO | Complete Sprint 4 backend suite plus permission contract: 35 passed, 0 failed; git diff --check clean. Admin portal search found no Product Intake UI, so frontend scope/branch approval is required before acceptance and closure. | Uncommitted Sprint 4 |
| 2026-07-30 | manual-20260730-s4-06-admin-ui | S4-06 | IN_PROGRESS | Located backend `admin-ui`; legacy Label Contributions navigation now opens the unified queue/detail Workbench with optional market filtering, assignment actions, comparisons, warnings, lazy evidence and review actions. `npm run build` passed. Permission-aware action visibility and admin-manual form remain before closure. | Uncommitted Sprint 4 |
| 2026-07-30 | heartbeat-20260730T122017Z | S4-06 | IN_PROGRESS | Product Intake UI now consumes the admin access profile: ADMIN_READ_ONLY cannot open private evidence or see write actions; reassignment is owner-only; supported markets are backend-aligned and no market defaults to TR. `npm run build` passed. Admin-manual form remains before closure. | Uncommitted Sprint 4 |
| 2026-07-30 | heartbeat-20260730T123517Z | Sprint 4 | DONE | Admin manual form creates INTERNAL_REVIEW candidates with backend-aligned market and nutrition enums; admin UI production build passed; full Sprint 4 backend suite: 35 passed, 0 failed; `git diff --check` clean. | Pending closure commit |
| 2026-07-30 | manual-20260730-s5-01 | S5-01 | DONE | Existing mobile branch approved; third CTA routes to localized two-photo capture, images are resized/re-encoded as JPEG on-device, and manual continuation remains available. Mobile `npm run typecheck` passed. | Uncommitted Sprint 5 |
| 2026-07-30 | manual-20260730-s5-02 | S5-02 | DONE | Provider-neutral lazy native OCR adapter with unavailable fallback, locale-aware nutrition parser, visible confidence warnings, editable review fields and barcode-keyed SecureStore draft recovery added. Mobile `npm run typecheck` passed; frontend `git diff --check` clean. | Uncommitted Sprint 5 |
| 2026-07-30 | manual-20260730-s5-03-core | S5-03 | IN_PROGRESS | Added SHA-256 metadata, idempotency-key session reuse, presigned XMLHttpRequest upload progress and finalize client. Mobile `npm run typecheck` passed; retry state persistence and UI integration remain. Automation recreation attempted twice but Codex app returned `No handler registered`; no automation is currently active. | Uncommitted Sprint 5 |
| 2026-07-30 | manual-20260730-s5-03-ui | S5-03 | DONE | Review UI shows per-asset progress and retry/error states; persisted idempotency key and unexpired upload session are reused after restart; finalize completes both assets together. Mobile `npm run typecheck` passed and frontend `git diff --check` clean. | Uncommitted Sprint 5 |
| 2026-07-30 | manual-20260730-s5-04 | S5-04 | DONE | Added finalized-session submit endpoint that creates an idempotent USER_OCR review case and attaches two verified private assets. Mobile UI requires explicit temporary-storage/admin-review consent, never grants public-media consent, and keeps optional Custom Food separate. Backend compile, 11 focused tests, mobile typecheck and both diff checks passed. | Uncommitted Sprint 5 |

| 2026-07-30 | manual-20260730-s5-05 | S5-05 | DONE | Added owner-scoped My Contributions list/withdrawal, profile entry, UPDATE_PRODUCT_EVIDENCE notification deep link and same-case two-asset resubmission; old evidence enters retention cleanup. Backend focused suites: 12 passed, 0 failed; mobile typecheck and both diff checks passed. | Uncommitted Sprint 5 |

| 2026-07-30 | manual-20260730-s5-06 | Sprint 5 | DONE | Corrected EN/TR encoding and Turkish parser labels; added accessible names, roles, selected/disabled states and localized low-confidence hints; removed the photo-free dead end by routing optional Custom Food separately. Mobile static QA: 2/2 passed; backend regression: 12/12 passed; both diff checks clean. | Pending closure commits |

| 2026-07-30 | manual-20260730-s6-01 | S6-01 | DONE | Approval transaction now emits immutable USER_SUBMITTED_LABEL/ADMIN_REVIEWED_LABEL evidence with case/field fingerprints, reviewer identity and schema provenance while leaving catalog publication and values untouched. Focused evidence/admin suite: 14 passed, 0 failed; Flyway versions unique; diff check clean. | Pending commit |

| 2026-07-30 | manual-20260730-s6-02 | S6-02 | DONE | Added allow-listed selected-field apply for APPROVED UPDATE_EXISTING cases; all selected values validate before mutation, unselected fields and publication metadata remain unchanged, and the admin comparison UI exposes explicit checkboxes. Backend evidence/apply suite: 17 passed, 0 failed; admin production build and compile passed; diff check clean. | Pending commit |

| 2026-07-30 | manual-20260730-s6-03 | S6-03 | DONE | APPROVED NEW_CANDIDATE cases are verified then published only through CatalogPublicationService with actor/reason/correlation audit context; central failure leaves the case approved and unapplied. Admin UI exposes the action only for the valid state/mode. Publish/apply/evidence suite: 22 passed, 0 failed; admin production build and diff check passed. | Pending commit |

| 2026-07-30 | manual-20260730-s6-04 | S6-04 | DONE | Existing-product apply and candidate publish now share a transactional catalog mutation orchestrator: selected-field audits, quality issue sync, canonical/search recalculation and product cache eviction execute with the case transition; failures propagate before APPLIED. Focused suite: 18 passed, 0 failed; compile and diff check passed. | Pending commit |
| 2026-07-30 | manual-20260730-s6-05 | S6-05 | DONE | Backend rejects unconfirmed publication and material nutrition changes (20% relative threshold); Workbench marks high-impact comparisons, confirms apply/publish and shows provider/value/basis/confidence/timestamp corroboration. Contributors receive decision notifications on rejection and only after real APPLIED/publication success. Focused backend suite: 26 passed, 0 failed; admin production build and diff check passed. | Pending commit |
| 2026-07-30 | manual-20260730-s6-06 | Sprint 6 | DONE | Real JPA concurrency test proves the second decision waits on the case PESSIMISTIC_WRITE lock; rollback/error paths retain APPROVED and suppress success notifications. Complete Sprint 6 suite: 45 passed, 0 failed; diff check clean. | Pending commit |
| 2026-07-30 | manual-20260730-s7-01 | S7-01 | DONE | Fail-closed global defaults; market-configured deterministic SHA-256 cohorts; internal dogfood override; kill switch evaluated before every user create/submit/resubmit; authenticated availability contract. Focused suites: 12 passed, 0 failed; compile and diff check passed. Context smoke remains blocked by the pre-existing LOCAL-storage conditional submission-service/controller mismatch. | Pending commit |
| 2026-07-30 | manual-20260730-s7-02 | S7-02 | DONE | Explicit email-configured internal dogfood bypasses market/cohort but remains kill-switch controlled; rollout decisions emit bounded reason+market metrics without user identity; availability contract always preserves SEARCH_MANUALLY and CREATE_CUSTOM_FOOD fallbacks. Focused suites: 14 passed, 0 failed; compile and diff check passed. | Pending commit |
| 2026-07-30 | manual-20260730-s7-03 | S7-03 | DONE | Rollout configuration accepts only 0/1/10/50/100; deterministic cohort proof over 2,000 identities remains monotonic through every stage; Spring binding opens EU and UK_IE together from configuration with no market-specific implementation. Focused suites: 9 passed, 0 failed; compile and diff check passed. | Pending commit |
| 2026-07-30 | manual-20260730-s7-04 | S7-04 | BLOCKED_EXTERNAL_EVIDENCE | Added versioned OCR/admin-capacity/retention thresholds, real-evidence template and fail-closed validator. Synthetic fixture returns PASS_WITH_RELEASE_GATE; empty real template returns BLOCKED with 11 missing measurements; storage policy VALID (30/90/1 days); retention suite 3 passed. Closure requires real 50+50 label corpus results, real queue/capacity and deletion rehearsal measurements, plus S3-PROVIDER-PROOF before release. | Pending framework commit |
| 2026-07-30 | manual-20260730-s7-04-reference-ocr | S7-04 | STOP_GATE | Open Food Facts reference OCR evaluation ran the unchanged mobile parser over 50 TR + 50 UK/EN nutrition labels without retaining images. TR coverage 4%/precision 50%; UK coverage 5%/precision 90%; both fail 90%/95% gates. Android/iOS GrunProductOcr native bridge is absent. Parser/native implementation must be fixed before user device acceptance. | Pending commit |

| 2026-07-30 | manual-20260730-s7-04-native-ocr | S7-04 | STOP_GATE | Added an autolinked local Expo OCR module: Android uses unbundled Google ML Kit and iOS uses Apple Vision. Android manifest + Kotlin compilation and mobile typecheck passed; Apple autolinking resolves the pod/module (device build remains external). Geometry-aware parser reference rerun improved TR to 20.5% coverage/65.85% precision and UK/EN to 79.5%/71.07%, still below 90%/95%; rollout stays stopped pending device corpus and parser quality. | Pending commits |
## Tracker update rules

- Set the active slice before editing implementation files.
- Keep an incomplete slice active when a focused test fails.
- Record exact commands and pass/fail counts; do not write only Ã¢â‚¬Å“tests passedÃ¢â‚¬Â.
- A blocked item must name the missing authority, credential, repository or
  product decision.
- Do not mark a sprint complete while a P0 acceptance item is skipped.
