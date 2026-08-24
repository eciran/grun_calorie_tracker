# Product Catalog August 8 Automation

This directory is the execution contract for the five-day catalog preparation
program in `docs/PRODUCT_CATALOG_AUG08_ROADMAP.md`.

## Outcome

By the August 8 handoff, produce the largest defensible test-build catalog and
a repeatable import bundle. Keep licensed production candidates separate from
private-test retailer candidates whose storage/commercial-use rights are not
documented.

## Run boundary

- Run only on `feature/unified-product-intake-review`.
- Acquire `scripts/product-catalog-aug08-automation-lock.ps1` before work and
  release it in a `finally` path.
- One run performs only the next incomplete stage in `STATUS.md`.
- Allowed write paths are this directory, the roadmap and handoff, dedicated
  August 8 catalog scripts, `sample-data/manifests/product-catalog-aug08-*`,
  and `outputs/product-catalog-aug08/`.
- During `D5-BUILD-GATES` only, a test-only fixture or test configuration may
  be changed when a failing test report proves the cause, the file has no
  pre-existing diff, and focused tests pass afterward. Never weaken production
  authorization or change application behavior to make a test pass.
- Preserve all unrelated working-tree changes. Do not commit, push, stash,
  reset, deploy, or write to AWS/production.

## Safety and provenance rules

- Never crawl or scrape Tesco. Its terms require prior written consent for
  automated extraction. A licensed feed or written agreement is a product
  owner gate.
- Never publish A101, Migros, IYAS or another retailer-page dataset to a
  production catalog until commercial reuse and persistent-storage rights are
  recorded in the source registry.
- Never overwrite a frozen source snapshot. Download to a dated `.part` file,
  verify a non-empty response, record byte length and SHA-256, then rename it.
- Do not convert package size into a dietary serving without review.
- Do not infer sodium from salt unless a versioned transformation policy is
  approved and recorded.
- A failed or missing gate remains visible; it is not relaxed to increase the
  product count.

## TR growth protocol

TR enrichment is the active product priority before returning to the general
build gate. Use `scripts/analyze-tr-product-capacity.mjs` and the frozen reports
as the baseline.

- First review the exact-GTIN OFF nutrition candidates. Promote only candidates
  with complete plausible per-100 nutrition, preserve OFF attribution and field
  provenance, and route the 46 duplicate-GTIN groups to duplicate review.
- Rebuild a new versioned `PRIVATE_TEST_ONLY` rich overlay; never overwrite the
  existing v1 files and never add retailer-derived identity/market evidence to
  the licensed-default bundle.
- Then address the 2,581 barcode queue, prioritizing the 1,925 rows whose
  nutrition is already complete. GS1, brand-owner, authorized supplier or user
  label evidence is acceptable; fuzzy name/package matching is review-only and
  can never manufacture a GTIN.
- For new discovery, inventory the 20,699 supplemental GTIN backlog, but count a
  product only after name, brand, exact identity, nutrition, market evidence and
  rights all pass. Do not crawl additional retailer pages.
- Record separate counts for production-allowed, private-test-ready, evidence
  candidates and raw identities. Never present an upper bound as achieved.
## Per-run protocol

1. Inspect the branch, working tree, this README, the roadmap, and `STATUS.md`.
2. Acquire the lock with a unique run ID.
3. Re-run the file audit and verify source/artifact hashes before selecting work.
4. Execute the next incomplete stage only; make it restart-safe and bounded to
   45 minutes.
5. Run focused validation and write evidence under
   `outputs/product-catalog-aug08/`.
6. Update `STATUS.md` with exact commands, counts, hashes, pass/fail, blocker,
   and next action.
7. Release the lock even when a command fails.

If a public download or Docker operation cannot run without interactive
approval, record `BLOCKED_APPROVAL` and continue with another read-only or
file-only stage. Never substitute an unapproved source.

## Completion

Set `PROGRAM_STATUS` to `READY_FOR_TEST_BUILD` only when the final manifest,
licensed production bundle, optional private-test overlay, validators, two-pass
isolated import rehearsal, backend tests, admin UI build, checksums, and
rollback notes all pass. Otherwise keep `IN_PROGRESS` while an evidenced,
safe next action exists; use `BLOCKED` only when owner input or external rights
are required.

For the current full-suite gate, isolate and verify one common root per run:
shared H2 test-context isolation, legacy admin permission fixtures, or missing
analytics revision mocks. Do not touch a file with a pre-existing diff, and do
not change production permission mappings without owner review.

If all gates pass before August 8, switch to read-only monitoring: verify hashes,
preflight the licensed bundle and re-run only bounded smoke/build checks. Do not
pause early. On or after 2026-08-08, perform one final read-only verification,
write the handoff summary, then pause the heartbeat automation.
