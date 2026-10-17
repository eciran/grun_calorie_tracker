# Configuration

This document explains how local and deployment configuration should be managed.

## Environment Files

For local development, create `.env` from the committed example:

```powershell
Copy-Item .env.example .env
```

Do not commit `.env`. Commit only `.env.example`.

## Local Defaults

Local defaults live in:

```text
.env.example
```

Use that file as the source of truth for required variables. The README intentionally does not duplicate the full environment list because it can expose implementation detail and make the public repository harder to review.

## Secret Handling

These values must be treated as secrets or sensitive operational configuration:

- `JWT_SECRET`
- `POSTGRES_PASSWORD`
- `GRUN_LOCAL_ADMIN_PASSWORD`
- `GRUN_LOCAL_DEMO_USER_PASSWORD`
- `GRUN_BREVO_API_KEY`
- `GRUN_REVENUECAT_WEBHOOK_AUTHORIZATION`
- production database credentials
- production mail/store/provider credentials

Local placeholder values are acceptable in `.env.example`, but real values must stay in `.env` or deployment secret stores.

## PostgreSQL Volume Note

PostgreSQL Docker volumes keep their original database password. If `.env` is changed after the first container initialization, the existing local volume will not automatically change its database password.

For local development, keep `.env` stable or intentionally recreate the local database volume when starting from a clean database.

## Mail Provider

Local development defaults to log-based mail delivery. Password reset and email verification links are written to the application log.

Production should use Brevo:

```text
GRUN_MAIL_PROVIDER=BREVO
```

Before enabling Brevo in production:

- verify the sender/domain in Brevo
- configure SPF, DKIM, and DMARC DNS records
- use a sender address on the verified domain
- keep the Brevo API key outside Git

## Rate Limiting

Local development can use in-memory rate limiting.

Production should enable Redis-backed shared rate limits:

```text
GRUN_RATE_LIMIT_REDIS_ENABLED=true
```

The local run script starts Redis through Docker Compose. If Redis is enabled but unavailable, the backend falls back to the in-memory limiter and logs a warning.

## RevenueCat

RevenueCat webhook authorization and product ids should be configured through environment variables or deployment secrets.

Backend smoke checks:

```powershell
.\scripts\smoke-revenuecat-config.ps1 -BaseUrl "http://localhost:8080" -AdminToken "<admin-jwt>"
.\scripts\smoke-revenuecat-webhook.ps1 -BaseUrl "http://localhost:8080" -WebhookAuthorization "<revenuecat-auth-header>" -UserId 1
```

Real App Store / Google Play sandbox purchase tests require a mobile build using the RevenueCat SDK.

## AI Cost Estimation

Admin AI monitoring uses backend-side estimated cost from provider token usage. Configure model-specific token prices through environment variables:

```text
GRUN_AI_OPENAI_INPUT_TOKEN_COST_PER_1M=0
GRUN_AI_OPENAI_OUTPUT_TOKEN_COST_PER_1M=0
GRUN_AI_OPENAI_COST_CURRENCY=USD
```

Keep zero as the repository-safe default and set both prices in deployment configuration for the selected model. For example, the current `gpt-5.4` rates are `2.50` input and `15.00` output per one million tokens. If `GRUN_AI_MODEL` changes, update both prices in the same release. Historical rows keep the cost metadata stored at request time. See `docs/AI_PHOTO_ALTERNATIVE_SNAPSHOTS.md` for the photo experiment baseline.

For invoice-grade reporting, use OpenAI organization Usage/Costs APIs as a separate provider-actual metric instead of replacing this request-level estimate.
## Resumable Product Catalog Audit

Migration V306 initializes a single audit checkpoint and indexed per-product outcomes. Scheduled execution is disabled by default; enabling the older quality-queue flag alone does not opt in to this worker. After deploying and validating V306, explicitly set:

```text
GRUN_PRODUCT_CATALOG_AUDIT_ENABLED=true
GRUN_PRODUCT_CATALOG_AUDIT_BATCH_SIZE=250
GRUN_PRODUCT_CATALOG_AUDIT_TIME_BUDGET=15s
GRUN_PRODUCT_CATALOG_AUDIT_LEASE=2m
GRUN_PRODUCT_CATALOG_AUDIT_MAX_ATTEMPTS=3
GRUN_PRODUCT_CATALOG_AUDIT_RETRY_DELAY=2m
GRUN_PRODUCT_CATALOG_AUDIT_REPEAT_INTERVAL=24h
GRUN_PRODUCT_CATALOG_AUDIT_TICK_MS=60000
GRUN_PRODUCT_CATALOG_AUDIT_ZONE=Europe/Dublin
GRUN_PRODUCT_CATALOG_AUDIT_DOWNTIME_START=01:30
GRUN_PRODUCT_CATALOG_AUDIT_DOWNTIME_END=05:00
```

The scheduler checks persisted state after startup and each minute. It starts only when READY, or when a successful pass is at least 24 hours old; interrupted RUNNING work resumes. FAILED and CANCELLED require an explicit admin restart. During Dublin downtime, including DST, scheduled ticks do no work. Manual commands are an intentional override, not delayed scheduled work. Disabling scheduling does not cancel a running manual step.

Admin endpoints share `/api/v1/admin/products/quality-audit`: GET status; POST `/start`, `/step`, `/cancel`; GET `/items?outcome=REVIEW_REQUIRED&page=0&size=50`. ADMIN role plus CATALOG_READ is required to read; CATALOG_MANAGE is required for commands. Start is idempotent while running. Step processes at most one bounded batch; repeat manual steps or enable the scheduler to finish the pass. Cancel fences the current lease; starting after cancellation/failed/completed state creates a new pass. Do not manually edit checkpoint tables.

Only audit state, existing quality issues and OPEN suggestions are written. Nutrition, names, barcodes, publication, servings and factual verification markers are not changed. No AI/provider API is called. RULES_PASSED means implemented deterministic checks passed, not independent source verification. A completed pass can still contain REVIEW_REQUIRED records.

The cursor is an ID, not a row count or percentage. Each pass captures a maximum catalog ID; later imports are included in the next pass. Per-product findings contain only the latest pass that visited that product; previous run summaries remain in the existing quality scan history. Capture a run's detail report before starting another pass if permanent detailed history is needed. Retries use a minority of each batch and bounded exponential backoff; dead letters fail the pass after healthy rows finish. Input hashes do not skip rows: rule/source changes are inspected in the next full pass. An active old ruleset requires cancel/restart after a ruleset upgrade. Increment RULESET whenever deterministic audit behavior changes.

The 15-second budget is checked between products; an in-flight product transaction can take up to its 30-second timeout. Lease expiry is recovery after worker loss, not permission for overlapping writes: ownership and state locks fence checkpoints. Stale-source refresh/delta maintenance remains a separate future work item, not part of this initial sweep.

## Reviewed Food Identity And USDA Subtype

V307 adds optional product fields `foodConceptKey`, `compositionVariantKey`, `sourceDataType`. They are source/identity metadata, not language-specific UI titles. CSV names are `food_concept_key`, `composition_variant_key`, `source_data_type`. Assigning concept/variant requires an approved CURATED_ADMIN input; existing reviewed identity cannot be reassigned by CSV. Leave legacy rows unmapped until their compatibility decisions are approved. A source-specific composition variant conservatively keeps distinct profiles separate; do not use a common display name to merge them.

USDA generic export supports Foundation/SR Legacy only and preserves the subtype. Branded is never inferred to be generic just because GTIN is absent. Missing subtype stays in review; fdc_id/source_key conflict and unknown/contradictory classification fail per row. CoFID/source candidates marked `PENDING_REVIEW` cannot be imported as approved products. No ML/density conversion is inferred from names or the concept id.

Audit ruleset is now `catalog-audit-r5-v1`. After deploying V306/V307 and the R5 worker, an active older ruleset must be cancelled/restarted explicitly. Audit reports subtype/identity conflicts plus EN/TR naming, Unicode, brand, beverage mass-basis/form, incompatible visible servings and multiple-default review findings, without editing product facts. Active localized names, canonical brand metadata, serving dimensions/defaults and source categories participate in input hashes. Scheduling remains disabled unless explicitly enabled. R5 introduces no density configuration, provider credentials or scheduled provider fetches: density must never be a universal environment default. The offline CoFID scripts need Python with openpyxl and must not be pointed at production apply paths. The legacy `build-liquid-ml-apply-package.ps1` now fails closed; use the source-pinned R5 review manifest, then the separate R8 backup/rehearsal/application gates.

R4 reviewed naming exceptions live in `catalog/source-name-decisions-r4-v1.json` and require exact source identity/description/subtype before native import fallback. `source_language=TR` applies Turkish casing to base CSV names; `_tr` localized columns and admin localization acceptance are always language-aware. Unknown official brand casing is preserved, not guessed. Six source corrections require serving/alias review before publication; exceptions never grant a g/ml conversion or override a reviewed C2 identity. Existing localizations are not overwritten by registry fallback. Details and source/format-only dry-run commands are in the product catalog remediation plan. No new environment variable is required for R4.

## Food Contribution Evidence Storage

Local development stores product-label evidence under the configured workspace directory. Production defaults to private S3 storage and fails startup when the bucket or region is missing.

```text
GRUN_FOOD_CONTRIBUTION_STORAGE_PROVIDER=S3
GRUN_FOOD_CONTRIBUTION_S3_BUCKET=<private-bucket>
GRUN_FOOD_CONTRIBUTION_S3_REGION=eu-west-1
GRUN_FOOD_CONTRIBUTION_S3_PREFIX=product-contributions
GRUN_FOOD_CONTRIBUTION_PRIVATE_BASE_URL=https://api.grun.app
```

The bucket must remain private. The backend uses the AWS default credential chain, applies S3 server-side encryption, validates JPEG/PNG/WebP signatures, calculates SHA-256 server-side, and streams evidence only through authenticated owner/admin endpoints. Do not configure a public bucket URL or commit AWS credentials.

## Bounded Product Snapshot Caches (V308/V309)

Product search, ID and barcode caches use their own `foodProductCacheResolver` and local Caffeine storage, even when the global cache manager is Redis. Other modules' cache managers/TTL policies are unchanged. V308 adds the writer fence; V309 improves the reader stamp without editing V308. Neither is a product-data cleanup or nutrient/portion migration. This implementation is locally verified; no standalone AWS rollout is authorized.

```text
GRUN_PRODUCT_CACHE_ENABLED=true
GRUN_PRODUCT_CACHE_NEGATIVE_TTL=5s
GRUN_PRODUCT_CACHE_COALESCING_ENABLED=true
GRUN_PRODUCT_CACHE_COALESCING_MAX_CONCURRENT_LOADS=128
GRUN_PRODUCT_CACHE_COALESCING_WAIT_TIMEOUT=1s
GRUN_PRODUCT_CACHE_MAX_PAYLOAD_BUDGET_BYTES=67108864
GRUN_PRODUCT_CACHE_SEARCH_TTL=30s
GRUN_PRODUCT_CACHE_SEARCH_MAX_ENTRIES=256
GRUN_PRODUCT_CACHE_SEARCH_MAX_VALUE_BYTES=65536
GRUN_PRODUCT_CACHE_DETAIL_TTL=5m
GRUN_PRODUCT_CACHE_DETAIL_MAX_ENTRIES=512
GRUN_PRODUCT_CACHE_DETAIL_MAX_VALUE_BYTES=16384
```

TTL is fixed after insertion, not extended by reads. The detail policy applies independently to ID and barcode caches. Default maximum serialized payload storage is 32 MiB across the three caches; keys, JVM/object overhead and temporary serialization allocations are additional. The 64 MiB configuration budget checks `search entries * search bytes + 2 * detail entries * detail bytes`; it is not a total process-memory limit. Startup rejects TTL outside 1s-15m, entries outside 1-10,000, per-value limits outside 1 KiB-1 MiB or a budget above 128 MiB. Oversized responses bypass storage without truncating API results. Pending transaction snapshots are separately bounded to 16 entries/1 MiB per cache per transaction.

Serialized snapshots are decoded to fixed DTO types on every hit, isolating nested product/serving mutations. Existing query, language, owner scope, market and pagination dimensions remain part of keys. Global `spring.cache.type=none` or the product enabled flag disables this cache. No Redis deployment is needed or provisioned by these changes.

Product-cache keys include a PostgreSQL revision stamp. V308 fences relevant writes with a shared transaction advisory lock and a monotonic sequence. V309 reads the sequence before and after a `pg_locks` snapshot of that exact database/advisory writer namespace; an active/pending shared writer or changed sequence returns no usable stamp. Unlike V308's initial reader implementation, readers no longer take competing exclusive session locks. Keep this sequence at CACHE 1, monotonic and not manually reset. Pending writers, failed/unavailable revision infrastructure and stronger-than-READ_COMMITTED snapshots bypass caching. Transactional puts/evictions publish only after commit; obsolete loaders are rejected and rollback never publishes uncommitted snapshots. Sequence gaps on rollback are harmless additional cache misses. The revision read failure guard uses a savepoint inside an existing transaction.

The product-only read advisor coalesces eligible same-cache/same-version/same-key cold calls outside their database transactions. The leader executes the ordinary Spring cache/transaction chain through completion; waiters then re-enter that chain and obtain independent DTO snapshots using fresh keys, not the leader's mutable result or exception. Coordination is local to one JVM, not distributed. The default 128 in-flight keys are bounded independently of cache entries. Waiters time out after 1s and fall through to normal loading; capacity exhaustion, disabled/unavailable cache and outer transactions bypass coordination. Recursive calls do not wait on themselves. Interrupted waiters preserve interruption and cancel without starting their loader. This is not a hard DB/provider concurrency limit: timeout/capacity bypass, non-cacheable failures, oversized responses and changing revisions can legitimately cause repeated loads. No coalescing around admin/import CacheEvict methods or other modules was added.

`negative-ttl` applies only to a cacheable search page whose content is empty AND totalElements is zero. It defaults to 5s, is clipped to positive search TTL, can be 0 to disable negative storage and cannot exceed 30s. An empty beyond-last page keeps its actual positive total and regular TTL; it does not call OFF. ID/barcode not-found exceptions and null responses are not negative-cached. OFF valid empty arrays are distinguished from HTTP/transport/invalid-body failures. Provider failures retain the existing empty API response contract but carry an internal nonserialized cache veto, so the next call can recover immediately. They also skip ordinary zero-result search telemetry/request IDs, avoiding false catalog-gap statistics; real empty results still follow the normal telemetry path. No public `cacheable` JSON field was introduced.

Normal advised calls perform a preflight stamp/key read and the existing Spring cache key read; successful cold stores also verify the stamp before publication. Warm requests are not zero-DB operations. Actual cold/warm overhead and the cost of frequent `pg_locks` inspection must be measured in the final production-equivalent release gate. Coalescing settings validate 1-1024 in-flight keys and 10ms-5s wait. Monitor `grun.product.cache.coalescing` results (leader/wait/timeout_bypass/capacity_bypass/recursive_bypass/interrupted/key_bypass); these counters are coordination outcomes, not counts of actual SQL/provider work. No product names, query strings or user identities are metric labels.

The migration covers product metadata, localizations, aliases, servings/labels, categories, markets, source categories/evidence, quality issues, canonical resolutions, duplicate-search groups and brands. Popularity-only `usage_count`/`search_selection_count` updates intentionally use TTL freshness instead of global invalidation on every selection. Updating a new product metadata column or introducing a new cached read-dependency table requires an additive migration refreshing the trigger contract. Restore/reset of the sequence or writes with disabled triggers require all application product caches to be restarted/cleared before traffic; never assume old generation keys are safe after a DB restore.

At consolidated release, rebase preparation/backup/schema gates from V307 to V309, review the added Caffeine dependency/advisor/config, verify runtime role sequence/function/table/lock-view permissions, validate Flyway and all 17 fence triggers, then replay the unchanged 60 live queries and actual cold/warm/multi-instance checks. Earlier V307/V308 apply packages and reports remain frozen historical artifacts, not automatically reusable inputs. Monitor `grun.product.cache.access` (cache/result) and `grun.product.cache.bypass` (reason); high write activity/oversized pages can legitimately reduce the hit rate. Source-data freshness, correctness and authorized import approvals remain separate from cache freshness.

Implementation references: [Caffeine eviction](https://github.com/ben-manes/caffeine/wiki/Eviction), [PostgreSQL advisory locks](https://www.postgresql.org/docs/18/explicit-locking.html#ADVISORY-LOCKS), [PostgreSQL sequence semantics](https://www.postgresql.org/docs/18/functions-sequence.html).

### Canonical External Search Replay (R7-B7)

When a first-page local text search truly has no matches, the existing trusted OFF publication pipeline may import branded candidates. The response is then rebuilt from the SAME local search specification and a fresh indexed candidate lookup, not a sliced provider list. Pending product/quality writes are flushed before JDBC lookup. Query/brand/calorie/nutri-score/preparation/market/type predicates, public eligibility, blocking quality issues, canonical/duplicate suppression, ordering and actual count/page metadata remain authoritative. Existing barcode identities are not overwritten. Duplicate provider rows cannot inflate the count; distinct barcodes are not merged just because names match.

Canonical category filters and explicit generic/local-dish catalog types never call OFF's branded fallback. Beyond-last pages retain local totals without fetching external products. Valid empty replies and provider failures retain the separate B6 cache/telemetry semantics. No new environment setting, migration, inferred translation/density or nutrition/portion change is introduced. Provider, publisher and evidence failures still share the existing writable search transaction; rollback must not publish cache snapshots. Same-key cache coalescing is not a cross-key unique-barcode import lock: simultaneous different searches importing the same absent barcode remain a separate acceptance gate. B7 is locally verified with mocked external replies and disposable H2/PostgreSQL, not deployed or certified against current AWS source data.

### Compound Search Qualifiers And Reviewed Brands (R7-B8)

Whole-phrase food synonyms must never drop preparation or composition words: a qualified Turkish chicken query is not expanded to unqualified chicken breast. Compound translation/reordered recall relies on stored active aliases/localizations, not guessed translations. B9 supersedes the old six-position boundary with the shared bounded full-query contract below; reviewed multiword brands are grouped before discarding short standalone tokens. Longest exact reviewed phrases retain subbrands such as Tesco Finest and M&S Food; unknown brand spelling and product identity stay unchanged.

Both final token predicates and PostgreSQL candidate lookup accept reviewed/separator-equivalent brand search keys. The candidate query remains parameter-bound, literal-LIKE escaped and a superset; more than5000 candidates still falls back to ORM. A necessary product token can provide the superset instead of a brand-only scan. Default exact brand/product ranking accepts reviewed brand segments before or after an exact stored/localized product name; it does not normalize the entire product name into a fuzzy identity. Query language/market filters, explicit sorts, pagination and all B7 visibility gates remain authoritative. No new environment value, migration, cache provisioning, catalog backfill or source certification is introduced. B8/B9 remain local-only pending the consolidated release; broader representative source relevance and current AWS performance still require their separate gates.

### Bounded Full Search Text And Source Replay (R7-B9)

Product q/query text accepts at most256 UTF-16 code units (Java String length, including supplied padding) and24 whitespace-delimited source words before reviewed-brand grouping. Within this limit, every token position is retained; preparation/composition words after position6 are not discarded. Existing minimum-two-character behavior for unreviewed standalone tokens remains. Limits are shared code safeguards, not environment/admin-adjustable SQL complexity settings. API, service, cache-key framing and indexed candidate lookup reject excessive input with IllegalArgumentException/HTTP400; no silent truncation, no incomplete query cache key. Null/category-only searches and q-over-query precedence remain unchanged.

The opt-in disposable PostgreSQL search suite additionally requires GRUN_R7_SOURCE_ORACLE_RECEIPT pointing to outputs/r5r6-common-live-20261013/applied/receipt.json. Its immutable SHA256 is pinned in src/test/resources/r7-reviewed-source-relevance-v1.json; missing/drifted/outside-outputs inputs fail closed. Thirty visible and two intentionally hidden EN/TR judgments run through both indexed and ORM search, without changing recorded facts/publication. The37 historical records are a limited USDA-heavy read projection, not a current AWS or serving/source-freshness certificate. This test-only receipt variable is never a production configuration value or permission to write to AWS; existing localhost-owned URL guards remain mandatory. No B9 migration/deploy/data operation is required or executed.

### Composition Word Boundaries And Mixed Source Tests (R7-B10)

Explicit salted/unsalted/tuzlu/tuzsuz search tokens require exact words in product names/display names or active aliases/localizations. Parentheses, hyphens, commas, slashes, colons, semicolons and NBSP act as boundaries. Salted must not match unsalted; a brand-only word cannot fulfill a composition qualifier. Use the dedicated brand filter for a brand whose name contains such a word. These constraints apply even to single-word queries and broad alias OR branches. Other partial token/brand searches are unchanged; indexed candidates can over-return and the final ORM specification remains authoritative. No sodium, preparation state, factual composition, serving or g/ml value is inferred or rewritten from these words.

The optional H2/owned PostgreSQL mixed source contracts use GRUN_RUN_MIXED_SOURCE_SEARCH=true and GRUN_R7_MIXED_SOURCE_CORPUS pointing to outputs/r7-mixed-source-search-20261014/cohort.json. The SHA256 is pinned in src/test/resources/r7-mixed-source-judgments-v1.json; original input hashes are verified too. PostgreSQL uses GRUN_R7_MIXED_TEST_URL and accepts only jdbc:postgresql://127.0.0.1:<port>/grun_r7_mixed_test. These are test-only variables, NOT production environment requirements. Without opt-in, ordinary tests do not depend on private artifacts; opted-in missing/drifted sources fail rather than being synthesized.

The35-record corpus preserves22 OFF GTIN variants and distinguishes five calculated/two estimated TR dishes from six unapproved CoFID candidates. CoFID pending CSV import is blocked; positive search is modeled only after explicit test-only publication in a disposable DB, NOT real source/production approval. Original candidate files stay pending and no density is inferred. Projected labels/base-name aliases and omitted category/source-evidence/portion/history/brand-relation tables are not current AWS certificates. B10 has no new migration, dependency, source release or deploy; previous backup/schema309/release/cache permissions and live acceptance gates still apply.

Reader-fence reference: PostgreSQL collects non-fast-path regular locks as a consistent snapshot; advisory key/database/mode filtering and the two sequence reads are necessary to distinguish writers from readers. [PostgreSQL pg_locks](https://www.postgresql.org/docs/18/view-pg-locks.html). Ordinary Spring cache calls do not themselves coalesce misses; this product-scoped advisor deliberately preserves the existing transaction/unless semantics. [Spring cache annotations](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html).

### External And CSV Import Identity Fence (R7-B11)

Managed runtime OFF search/barcode imports and CSV imports share transaction-scoped advisory key (17491,310), reserved separately from the cache writer fence (17491,308). Fetch the provider response or parse CSV BEFORE taking this fence, then recheck/load existing identities under the fence BEFORE any import writes. The entire DB phase is serialized, including evidence and publication, until commit/rollback. This conservative common lock avoids opposite batch-order and nested-lookup deadlocks; it is not a high-throughput per-GTIN scheduler or an authorization to serialize unrelated modules.

The participating import path requires a writable PostgreSQL READ COMMITTED transaction with auto-commit disabled. Stronger snapshot isolation fails closed because a post-lock lookup might otherwise use an obsolete snapshot. The lock statement has a fixed five-second JDBC query deadline; failures propagate and roll back, and must not be cached as a successful empty search. Large curated batches can cause runtime import contention/timeouts: measure this on the target AWS resource budget before release, use controlled batches and avoid simultaneous bulk growth during user traffic. Existing-row reads do not take this lock. H2 is an isolated test contract only; distributed concurrency is accepted only on disposable PostgreSQL. No new migration, paid Redis, configuration key or historical data rewrite is required.

This protects participating runtime/CSV producers only. The existing unique normalized-barcode/source-key indexes remain the final backstop against other writers; reviewed admin/native operations and manual publication still need their own current-record, locking and release gates. No blanket promise about all writer paths or source factual correctness is made.

### R5 Source-Only Portion Review And Rehearsal

scripts/review-r5-source-followup.py accepts pinned historical review/decision files, current complete OFF receipts and USDA batch files with exact-ID/hash/fetch-time receipts. It has NO database/client/deploy arguments. The full report distinguishes source facts from archived DB comparisons and quarantines rows touched by later provided operation receipts. Every exported row is REVIEW_ONLY_ARCHIVED_BASELINE; ordinary R5 patch generation rejects it. Fresh provider data cannot renew the timestamp or approval of an old DB snapshot. The actual release needs a new current before-image, source recheck and explicit approval/backup gates.

R5 gate V2 regenerates source-defined options and compares every canonical fingerprint. Half/quarter pieces require exactly one source-defined medium/unqualified whole parent; source multi-amount measures retain their complete stated mass. Source cup mass is not glass volume. Only an explicit source-defined ml/gram pair on the same beverage/juice identity can provide a physical-unit bridge; no density is propagated to other products and the original nutrition basis/numbers remain unchanged. Existing admin/equivalent-option, all21-field comparison, identity/preparation and unknown-nutrient guards stay in force. Matching scalar values do not attest legacy micronutrient units.

For API clients, portionSize=1 with a servingOptionId means ONE complete selected named portion, even when option.quantity describes a source half-piece,0.5cup or3oz measure. Its gramWeight/mlVolume already belongs to that full named choice; do not multiply by option.quantity again. Absolute MILLILITER input on PER_100G still requires the exact verified option's gramWeight AND mlVolume and that servingOptionId. Without a pair it must fail, not assume1ml=1g. This is not confirmation of mobile UI acceptance.

scripts/rehearse-r5-source-followup.py creates an owned PostgreSQL r5test fixture only. It refuses non-append-only or non-review-only source rows, and generated apply SQL rejects other DB names. Its scope is historical patch/undo safety, not a full RDS restore or current production approval. No AWS key, connection URL, role or production backup is requested or inferred.

The shared USDA source collector supports --batch-size1..20 (default20) for large Foundation profiles; use a new evidence directory when changing size. Never join partial nutrient fields or restamp expired batch receipts. Official FoodData Central [API guide](https://fdc.nal.usda.gov/api-guide/) and [data documentation](https://fdc.nal.usda.gov/data-documentation/) distinguish exact food details and source data types; collector keys remain in process-only headers, not artifacts.

### R5 Typed Nutrient Review (Gate V3)

The source-only gate retains typed alternatives for USDA Vitamin A1104/IU and1106/ug-RAE, Vitamin D1110/IU and1114/ug, and total sugars2000/1063. It does not choose a canonical unit or silently equate definitions. A same-number observation, including two zero values in different forms, is not evidence of the legacy scalar's unit. sourceName/unit/semantic/value and all candidate IDs are kept; selectedCandidate is always null and conversion/numeric-write/unit-attestation flags remain false. Unknown current fields are not filled and no missing source value becomes zero.

When an old scalar comparison says DIFFERS or SOURCE_VALUE_REQUIRED but a typed alternative exists, the review routes it to UNIT_OR_FORM_ATTESTATION_REQUIRED or SOURCE_DEFINITION_REVIEW_REQUIRED, retaining legacyComparisonState and the original holds. Duplicate/invalid-unit/invalid-value source alternatives produce SOURCE_VARIANT_CONFLICT even if another field matches. Such states still block the full review gate. This does not remove source identity/preparation/freshness/admin/portion constraints or permit a reference-basis correction.

Every source-followup row carries workQueue for deterministic offline/admin handoff. Each product belongs to exactly one next-action queue, while all overlapping hold reasons remain visible. Later-operation rebases and prepared proposals are separate from held cases. The JSON remains REVIEW_ONLY_ARCHIVED_BASELINE with no database/deploy connection; routing a row does not approve it. No runtime environment variable, database migration, backend public DTO or admin UI change is introduced by this diagnostic sprint.

Optional broader source tests inherit the B10 opt-in flag and additionally require GRUN_R7_RETAILER_SOURCE_CORPUS pointing to outputs/r7-import-race-20261015/cohort.json. src/test/resources/r7-retailer-dish-judgments-v1.json pins that59-row projection and its original input hashes. PostgreSQL uses GRUN_R7_RETAILER_TEST_URL and permits ONLY jdbc:postgresql://127.0.0.1:<port>/grun_r7_retailer_test. These variables are test-only. Archived estimated TR dishes stay estimated and six CoFID candidates remain pending outside explicit disposable approval simulations. Category/serving/canonical dependencies and current provider freshness are not inferred from the projection. No AWS operation is performed by this sprint.

### R5 R3/R4 Prerequisite Linkage (F3)

scripts/review-r5-classification-prerequisites.py consumes the pinned F2 source review, R3 compatibility manifest, R4 naming manifest/registry and original fresh USDA batch receipts. It checks shared ancestry, exact IDs/keys/body hashes, historical overlapping metadata and the original24h source age. It requires an exact selected-row count (default132) and a NEW output directory; it never refreshes evidence timestamps or overwrites accepted output. It has no DB URL, AWS client, deploy, apply or SQL option.

Only the R3 catalog_type change and exact allowlisted R4 source/display/preparation fields are projected into private copies for the existing R5 gate. Unmeasured source-data-type/identity fields are not added. EN/TR localization plans and exact metadata undo values/field-presence markers are review evidence only. Manufacturer-specific sources remain held even when classified SR Legacy and missing GTIN; fdc168876 explicitly names Uncle Ben's and must not become generic solely through the old R3 map.

Output stays REVIEW_ONLY_ARCHIVED_BASELINE with all live/apply/nutrition-write permissions false. Existing approvals, every nutritional comparison, child record, reference, default and prior serving are retained; metadata resolution cannot clear a serving-review or alias/admin requirement. Missing source preparation is not guessed; explicit coarse COOKED may be retained when the same source literally states cooked plus a specific method, without narrowing the stored state. R4's reviewed exceptions stay exact. Before any future use, obtain current full DB/alias/localization/reference before-images, reconcile brand/serving holds, refresh expired sources, approve a new guarded operation and include it only in the consolidated product release. This diagnostic introduces no runtime config, migration or public API change.

### R5 Offline EN/TR Name And Alias Proposals (F4)

scripts/prepare-r5-source-names.py accepts the SHA-bound F3 review, original R3 manifest and unchanged complete USDA batch receipts. The exact118-row selection must match source-name-proposals-r5-f4-v1.json, including IDs and exact official descriptions. Source receipts keep their original24h windows; missing, changed, duplicated, future or restamped evidence fails before output. Use a new output directory. There is no DB/AWS/apply/SQL option.

The new resource is an OFFLINE proposal registry, deliberately not loaded by runtime FoodSourceNameRules. EN/TR wording is internally prepared, not an official USDA Turkish translation. Source-specific species, cultivar and cut names without a verified local equivalent are retained rather than replaced with a similar local food. Source-derived qualifier checks protect salt/preparation, maturity/edible part, enrichment/egg, cut/trim/grade and species/cultivar distinctions. Fat trim is not a fat-free claim. Display and short labels both retain the complete profile. All236 proposed labels were checked against existing Java normalization/naming rules; this does not certify live ranking or source-unit correctness.

Each record proposes one exact official EN source-description alias and two whole-name EN/TR preparation reorderings. No qualifier is removed to create a broad alias, and original aliases are never carried forward automatically. Candidates use SYNONYM with active=false and applyAllowed=false. Duplicate/cross-profile names or aliases within the cohort block output; current full-catalog collisions, alias/localization row IDs and prior inactive/admin decisions are unmeasured and require a fresh separate rebase. Do not infer that an old alias should be deleted because the proposed display label differs.

All output remains REVIEW_ONLY_ARCHIVED_BASELINE with currentDatabaseBeforeImageRequired=true and no nutrition-write/live-apply/runtime-enable permission. The full21-field comparisons, typed variants, prior portions/defaults, preparation, basis, source keys, references and approvals remain protected. Only source name/display metadata is projected into private copies to re-run R5; unresolved unit/form and approval gates stay held. Actual activation must use a future explicitly reviewed operation with fresh before-images, exact alias collision/retirement decisions, backup/rehearsal and the consolidated release. No env key, migration, public API or admin UI change is introduced here.

### R5 Direct Typed RAE Evidence And Export Contract (F5)

scripts/review-r5-typed-reattestation.py inspects exactly87 F4 unit-review records against unchanged complete USDA batch receipts. It requires the pinned review/summary, original source timestamps within24h, matching IDs/body fingerprints, all original nutrient comparisons and the strict usda-typed-nutrient-contract-r5-v1.json. Use a new output directory. It has no DB/AWS/apply/SQL connection or option. The historical CSV/builder are corroboration only; a matching number and builder map do not prove that a particular import executed or certify legacy scalar units.

Only exact nutrient1106/number320/Vitamin A, RAE/ug becomes a NEW source-evidence proposal in MCG_RAE per100g. Original micro symbols normalize to ug without changing amounts. No retinol/IU conversion, closest-number source selection, missing-value fill, rounding-tolerance numeric replacement or old evidence rewrite is permitted. Unknown/mismatched current values, malformed source forms, bad units/amounts, duplicate records and evidence identity conflicts remain held. Even a matching zero cannot attest an old unit. [USDA source documentation](https://fdc.nal.usda.gov/Foundation_Foods_Documentation/) distinguishes RAE values; [NIH Vitamin A documentation](https://ods.od.nih.gov/factsheets/VitaminA-HealthProfessional/) describes form-dependent equivalences. This sprint uses the already reported RAE value, not a universal conversion factor.

The new shared export contract also fixes A=1106/320/ug-RAE, D=1114/328/ug-D2+D3 and sugar=2000/269/g (not1063 as an implicit alias). scripts/export-usda-fooddata-generic-products.ps1 validates these exact tuples, prefers the exact FDC nutrient ID and permits the exact legacy nutrient number only when the ID is absent and declared units match. Contradictory ID/number metadata, duplicate identities, invalid units or nonfinite/negative/boolean values fail; absent exact records stay null. Broad nutrient-name matching is no longer used for these three fields. Existing source one-decimal export rounding remains unchanged; both sourceAmount and exportedValue are retained, and numericConversion=false means no physical-unit/form conversion, not absence of export rounding.

New CSV exports include nutrient_provenance_json with source key/contract and typed A/D/sugar metadata. This optional header is preserved in the export artifact but is NOT currently consumed/persisted by the ordinary CSV importer. Do not claim typed ledger storage, whole-catalog unit migration or old-unit attestation from its presence. FoodProductSourceEvidenceEntity still lacks separate nutrient-unit/form/source-nutrient-ID columns; current PER_100_G basis metadata alone is not a nutrient unit. Implement guarded typed provenance storage/ingestion before any proposal can clear a release gate, preserve existing evidence/history, obtain fresh DB before-images and explicit approval, and include approved work only in the consolidated release.

Offline checks: run scripts/export-usda-fooddata-generic-products.ps1 -RunRuleTests without an API key; scripts/test-usda-typed-export.ps1 -OutputDirectory <new-test-output> runs a process-local mocked API/CSV replay with an inert test key, no provider traffic. Both current PowerShell and Windows PowerShell were exercised. Wrong-unit sources must not emit an import CSV, and IU-only sources must leave A empty. No runtime env key, migration, admin UI or API payload change is introduced by F5. Existing full R5/admin/alias/current-record/source-freshness gates stay open until their actual approved operation.

### R5 Append-Only Typed Source Ledger (F6)

V310 adds food_typed_nutrient_evidence without altering or backfilling food_product_source_evidence. Source nutrient ID/number, normalized declared unit, form/semantic, canonical unit, PER_100_G basis and exact decimal amount are stored with the complete original submitted source JSON/SHA256, original observation time, current parent/legacy before-image and its digest, reviewer, reason and idempotency fingerprint. Original unit spelling remains inside source_json. UPDATE, DELETE and TRUNCATE are refused by DB triggers; the food FK is restrictive, not cascading. Failed transactions can roll back their uncommitted insert. Committed evidence is historical: correction requires a separately reviewed append, never destructive undo of evidence.

The admin-only API base is /api/v1/admin/products/{productId}/typed-nutrient-evidence:

- POST /preview accepts field (vitamin_a, vitamin_d or sugar), complete sourceJson, exact UTF-8 sourceSha256 and original observedAt. It compares the exact Foundation/SR Legacy FDC identity and selected source nutrient with the current parent scalar; no IU/name/closest-number fallback or numeric conversion. Exact decimal parsing prevents tiny source differences being rounded into equality. Missing/invalid/current numeric differences, conflicting prior typed observations, unsupported catalog/basis or expired/future24h receipts remain held.
- POST to the base additionally requires approved=true, approvalReason (10..1000 characters), the returned expectedCurrentFingerprint and an authenticated ADMIN principal. Reviewer identity is server-derived. The parent FOR UPDATE lock serializes appenders; existing legacy rows are read FOR SHARE. All parent columns (including all21 nutrient values), except usage_count/search_selection_count, plus all ordered legacy evidence are fingerprinted as PARENT_AND_LEGACY_EVIDENCE_V1. Drift returns409 and requires a new preview. A15-second transaction bound applies. Identical repeats return the original evidence ID; simultaneous identical approvals insert once.
- GET lists the latest100 summaries without exposing the private source/before body or reviewer. GET /{evidenceId}/comparison reads the stored observation and returns current scalar/source-age/identity checks plus currentBeforeImageMatches. Stored rows are not rewritten when the current product changes. Preview and stored comparison use repeatable-read transactions.

Runtime property grun.product.typed-evidence-write-enabled defaults to false; no local/AWS secret or deployment setting was enabled by F6. Preview/history are available independently of the append switch. Keep the switch OFF until the consolidated product release and explicit operational approval. This ledger is NOT a nutrition correction/import mechanism and does not change food rows, aliases, serving options, existing approvals, quality issues, cache revision or user history. The ordinary CSV nutrient_provenance_json header remains untrusted and is deliberately NOT auto-ingested or self-approved. OFF/CoFID/other fields remain outside this exact USDA v1 contract.

Trust is explicitly ADMIN_REVIEWED_NOT_PROVIDER_FETCH_VERIFIED: the API validates the submitted receipt/body binding and original declared age, but does not fetch USDA or cryptographically authenticate a caller's timestamp/source claim. A SHA match is integrity, not provider authenticity. It records a reviewed source observation, never legacy-unit attestation. Every comparison retains FULL_NUTRITION_COMPARISON_REQUIRED, ALIAS_SERVING_REFERENCE_REBASE_REQUIRED and CONSOLIDATED_RELEASE_APPROVAL_REQUIRED. Parent/legacy drift protection is not a full source-nutrition or alias/localization/serving/reference rebase and cannot clear those release gates.

scripts/prepare-r5-typed-evidence-ingestion.py verifies the pinned87 F5 proposals, predecessor hashes and original complete batch receipts. It produces unapproved preview requests with expectedCurrentFingerprint=null and approved=false in a NEW directory. It has no DB, HTTP, AWS, apply or approval option. Source timestamps are not refreshed; expired receipts fail and need a new real source fetch/review. Old archived fingerprints must never be copied into the runtime current-fingerprint field.

Local test gates: GRUN_RUN_TYPED_EVIDENCE_POSTGRES=true and GRUN_TYPED_EVIDENCE_TEST_URL=jdbc:postgresql://127.0.0.1:<owned-port>/grun_typed_evidence_test. Test code refuses other DB names/hosts and uses r5_test with no production credentials. A complete Flyway chain and real PostgreSQL append/repeat/locking/immutability/rollback checks run there. The87-row rehearsal deliberately projects the separately reviewed generic classification and synthetic test-time freshness into disposable fixtures, then rolls back those87 observations; it is not a fresh AWS comparison, provider fetch or authorization of archived data. Accepted preview bundles retain the REAL receipt times instead.

### R5 Dependency-Aware Current Comparison (F7)

F7 extends the comparison fingerprint to PRODUCT_PARENT_LEGACY_AND_FK_DEPENDENCIES_V2. No migration or rewrite of V310 is introduced. Old source/evidence bodies, original receipts and accepted F6 data packages remain historical and unchanged. The append feature still defaults OFF.

FoodProductDependencySnapshot discovers incoming PostgreSQL foreign keys to public.food_items and public.food_item_serving_options. The reviewed product-dependency-fk-contract-v310.json requires all31 known paths:27 product references and four option references. Names/localizations, aliases, category/market/source-category links, servings, quality/review records, canonical/duplicate survivors, user-log/favorite/grocery/plan/template/recipe links and option labels/consumers are included. Additional supported declared FK paths are discovered automatically and change the fingerprint even when empty. Missing expected paths, composite/non-bigint/unvalidated/non-public/unsupported relations, more than64 paths, more than4096 options or a shared4096 dependency-row budget overflow make dependencies.complete=false and add DEPENDENCY_REBASE_INCOMPLETE. Examined row counts are NOT whole-table totals. Query/transaction timeouts refuse the operation; partial results never become approval.

All31 FK definitions are fingerprinted in the layout. The legacy source ledger is already retained in full in the parent/legacy part. The typed append-only ledger is excluded from dependency CONTENT hashing so it cannot invalidate its own idempotent retry; selected-field typed conflicts remain independently checked. The other29 paths contribute sorted SHA256 row-content digests/counts. Child user-history payloads are hashed inside PostgreSQL and are NOT copied to the response or before_json. Product parent and old source evidence retain the earlier full before-image representation. Changes to usage/search popularity counters alone remain excluded from parent invalidation.

Read-only preview/stored comparison retains repeatable-read consistency. Append locks the parent FOR UPDATE, all measured serving rows FOR UPDATE (protecting new option-only FK consumers), and existing dependent rows FOR SHARE in deterministic path/row order. A label edit, option/default change, newly linked recipe/log/template or schema dependency change between preview and append refuses the old approval. Waiting concurrent writes may proceed after commit; later stored comparison then reports the drift and dependencyRebaseRequired=true. No dependent row, history snapshot, alias, serving, default or parent nutrient is updated by this operation. These locks require the normal Spring transaction proxy; they are not a durable maintenance lock between separate requests.

Stored V1 evidence is read using its ORIGINAL parent/legacy scope. It is never rewritten or retroactively granted V2 dependency coverage:storedSnapshotScope remains PARENT_AND_LEGACY_EVIDENCE_V1 and dependencyRebaseRequired=true, even when its old parent-only comparison matches. Unknown historical scope is refused. New V2 evidence stores the expanded fingerprint; any old approval fingerprint must be regenerated by a fresh preview.

The V2 prep script emits requiredRuntimeSnapshotScope on every unapproved request, retaining expectedCurrentFingerprint=null, approved=false, original source age and every prior hold. F6 artifacts are not overwritten. No new source/DB/AWS connection or automatic approval was added.

This is a declared-FK DRIFT guard, not a complete catalog correction approval or recovery package. It does not prove alias translations, detect all cross-product alias/name collisions, certify nutritional facts or regenerate full child-row undo images. Non-FK IDs embedded in arbitrary JSON/text and outgoing category/brand definitions are outside this scope. The existing full-nutrition/alias-serving-reference/consolidated-release holds remain. Schema migrations/DDL must be operationally separated from approved data application; row locks are not a concurrent-DDL safety certificate. Wider alias namespaces, exact operation-specific child before-images, source freshness, backup/restore, production capacity and live AWS acceptance remain release gates. No current AWS dependency scan is claimed by disposable-fixture tests.

### R5 Remaining Nutrient Differences (F8)

scripts/review-r5-nutrient-differences.py consumes ONLY the pinned F2 NUTRIENT_DIFFERENCE_OR_MISSING_SOURCE queue, exactly22 rows. It verifies the archived whole before-image, complete provider identity/body hash, original timestamp within24h, original ancestor/receipt hashes and identical full21-field numeric comparisons. No DB, HTTP, AWS, apply, approval, secret or SQL client is exposed. Output requires a NEW directory. F1..F7 data packages and their original timestamps remain unchanged.

USDA source observations use exact nutrient ID/number/name/form/unit:potassium1092/306/Potassium, K/mg; vitamin E1109/323/alpha-tocopherol/mg; B121178/418/ug. Duplicate/missing IDs, another vitamin form, wrong units, bad numbers, nonfinite/negative/boolean/string amounts fail. The canonical SOURCE units are MG/MG/MCG. They do not attest the existing database scalar's unit or prove an old zero was measured. Nonzero mineral differences are not silently accepted by widening tolerance or rounding to the closest source. Source values remain reported profile values, not a claim of identical composition across every physical food sample.

This OFF cohort uses explicitly g-labelled native100g/100ml entries. Sodium is retained both as the original source grams and a separately labelled canonical SOURCE amount in mg. This is a source-unit conversion only, NOT conversion of an old DB value, nutrition reference, portion or gram/ml bridge. Conflicting declared/display values, inequalities, ambiguous reference bases or unsupported units/schemas fail rather than being guessed. Missing fiber stays sourceFact=null; an existing zero is retained in the before-image, not certified, replaced or filled. An explicitly reported zero and an absent source field remain distinct.

The accepted22-row review has24 field findings:seven OFF sodium unit/legacy-zero reviews, four missing-source fiber reviews, ten positive USDA vitamin observations on eight zero-valued profiles, and three nonzero USDA potassium differences.20 exact-source facts are prepared; zero products are approved for release. The other240 F2 rows,462 original field comparisons, options, pending changes, old evidence and every original hold are retained. Only review routing/source metadata is added. No new nutrition changes or lifecycle approvals are emitted.

existingTypedLedgerSupportsThisField=false is intentional for these new fields:V310 and the current API contract support A/D/sugar only. Do NOT POST the new sodium/fiber/potassium/E/B12 reports to that endpoint, weaken its allowlist or treat an offline fact as an approved correction. Extending guarded typed-field storage, reconciling forward OFF/USDA canonical-unit boundaries and obtaining explicit whole-current-record nutrition/portion decisions are separate required work. Existing legacy mixed/unknown-unit values must not be multiplied globally based only on provider name. Runtime imports/normalizers/mobile payloads were not changed by F8.

### R5 Versioned New-Field Source Observations (F9)

F9 adds a SEPARATE typed-source-observation-r5-v2 contract and source-only API. The original usda-typed-nutrient-contract-r5-v1 resource, A/D/sugar DTO allowlist, endpoint and matching-scalar append policy remain unchanged. F8 support flags describe the historical V310 implementation, not the new V311 path; do not rewrite those reports. This is evidence storage, NOT a general CSV importer upgrade or permission to overwrite food values.

Admin endpoints below /api/v1/admin/products/{productId}/typed-nutrient-evidence:

- POST /source-observations/preview accepts contractVersion=typed-source-observation-r5-v2, explicit provider/field, complete sourceJson, its exact SHA256 and ORIGINAL observedAt.
- POST /source-observations requires the existing OFF-by-default grun.product.typed-evidence-write-enabled switch, explicit approved=true/source-only reason, authenticated admin identity and the fresh preview currentFingerprint. The shared history and stored-comparison endpoints dispatch old/new records using their ORIGINAL contract version.

Supported USDA fields are potassium1092/306/Potassium, K/mg/MG; vitamin E1109/323/Vitamin E (alpha-tocopherol)/mg/MG; vitamin B121178/418/Vitamin B-12/ug/MCG. Exact integral IDs, one nutrient instance, textual number, exact form/name, Foundation or SR Legacy, numeric finite nonnegative bounded amount and native PER_100G are mandatory. Micro symbols are normalized, not numerically converted. No IU, fallback nutrient, alternate definition or closest-number selection is allowed.

Supported OFF fields are sodium and fiber ONLY. Full product object must contain a valid string GTIN, explicit nutrition_data_per=100g or100ml and an exact g-labelled *_100g nutrient value. This narrow legacy representation uses the explicit native reference even though the source field suffix is *_100g. Optional display/value fields must agree; inequalities, ambiguous basis, strings/booleans/null amounts and unsupported units are refused. Source sodium grams are multiplied by1000 ONLY in the canonical SOURCE observation (MG); fiber stays G. Missing fiber is refused; an explicitly reported numeric zero is distinct. There is no food-name liquid inference, density assumption or gram/ml conversion. Current source key/provider/barcodes, branded identity and nutrition reference must match; otherwise append is held.

V311 is additive. Existing V310 records keep every original column value; contract_version/declared_source_amount/source_field stay NULL and observation_only=false. No UPDATE/backfill or restamping of old evidence occurs. Old immutable triggers and restrictive FK remain. New records use contract_version=v2, observation_only=true, separately stored declared_source_amount/source_unit and canonical source_amount/canonical_unit, native basis and source_field (OFF only; USDA IDs remain exact). DB CHECK enforces provider/version/field/ID/form/unit/basis/conversion tuples and explicitly rejects SQL NULL bypasses. Original provider body/hash/time and current full V2 dependency before-image are retained.

The v2 source-only append may retain CURRENT_NUMERIC_REVIEW_REQUIRED while recording a source fact. It does NOT clear that hold or approve a correction. Identity, freshness, dependency completeness/drift and conflicting prior typed provider/key/basis/form/unit/amount still block append. Repeated identical requests return the same ID; parent/dependent row locks and transactional rollback remain in force. legacyUnitAttested=false, nutritionWriteAllowed=false, currentScalarUnit=UNATTESTED_LEGACY_UNIT and canonicalCurrentComparison=NOT_COMPARABLE_WITHOUT_LEGACY_UNIT_ATTESTATION remain explicit, even when scalar numbers match. Full nutrition, wider alias/reference and consolidated-release holds remain. SHA verifies submitted-body integrity, NOT provider authenticity:trust stays ADMIN_REVIEWED_NOT_PROVIDER_FETCH_VERIFIED. No source network request occurs in these methods.

scripts/prepare-r5-source-observations.py verifies the pinned F8 report, ancestor/source hashes, complete receipts, exact facts and unchanged timestamps within24h. It emits20 UNAPPROVED v2 preview requests on18 products, plus four missing-fiber holds without requests, into a NEW output directory. expectedCurrentFingerprint=null and approved=false; archived fingerprints cannot be used for runtime approval. The script has no DB/network/AWS/apply switch, never restamps expired receipts and never emits nutrition patches. A later use requires newly fetched/reviewed evidence once the real original receipt expires.

Local PostgreSQL proof projects historical source identities/classification/native basis into disposable fixtures, explicitly uses TEST-only synthetic request freshness, and rolls back all20 source observations. It is not current AWS measurement, source refresh or deployment approval. Production data, normalizers, mobile payloads and the 181k catalog are unchanged. Current full rebase and explicit nutrition/portion decisions remain required before the eventual single approved product release.

### R5 Four-Stage Comparison Loop (F10..F13)

scripts/audit-r5-comparison-cycle.py runs four ordered READ/REVIEW stages, never DB updates:full21-field diagnostics and typed source facts; unit/form/missing-data routing; source-qualified portion review; consolidated hold/rebase report. It selects exactly262 pinned F2 products. Existing source snapshots, receipts and all prior holds stay immutable. Historical numeric comparisons are explicitly legacy diagnostics, NOT proof that units/forms agree. New offline facts do not widen the existing typed ledger/API allowlists.

Use --archived only to analyse the frozen F2 before-images. Every output then says ARCHIVED_262_REVIEW_NOT_CURRENT_DB and currentDatabaseMeasured=false. No fake clock/receipt-refresh CLI option exists. The real source receipt is retained; stale evidence remains SOURCE_REFRESH_REQUIRED even when a new analysis is generated. --resume verifies the same code/input/freshness context and every accepted stage digest, preserving old stage times/bytes; changed or missing artifacts, code drift, crossed expiry, wrong stage order/path or uncommitted orphan files fail closed. Checkpoint/stage writes use atomic local replacement. An output directory cannot be silently overwritten by another run.

Actual current review requires --snapshot and --snapshot-proof from the NEW r5-comparison scope of collect-r3-source-snapshot.py. The proof must bind exact selected count, CSV digest, successful readonly task, known schema307..311 and actual read time within24h. Prior r5-release/serving snapshots cannot be re-labelled as this scope. The exporter uses REPEATABLE READ READ ONLY, explicit readonly PGOPTIONS,90s statement/3s lock timeout and at most300 unique positive IDs (the accepted selection has262). It never creates DB objects or writes parent/child/evidence rows. The standalone AWS task is transient and must be stopped/deregistered by the existing collector cleanup; do not leave persistent staging resources running.

The new query retains bounded parent/product metadata and caps each child/evidence array at4097 sentinel rows.29 declared dependent-content paths contribute in-DB SHA256 row hashes instead of user-history payloads. The target31 FK definitions, schema metadata and total4096 dependency budget are checked; unexpected/missing/unsupported paths or overflow leave complete=false. Old-source-evidence overflow has an independent sourceEvidenceComplete=false hold. A deployed pre-V310 schema may lack the typed-evidence FK:never pretend that current read is already target-runtime complete. Outgoing brand/category meanings, arbitrary JSON references, global alias collisions, typed-ledger conflict checks and runtime approval remain separate. currentReviewFingerprintNotRuntimeApproval must never be submitted as expectedCurrentFingerprint.

Identity drift quarantines only the affected record's OLD source comparison. It retains the current parent, marks SOURCE_IDENTITY_CHANGED_OLD_EVIDENCE_QUARANTINED and emits no source/portion proposal from that old provider body. It is not an automatic source-key repair or permission to reapply an old operation.

Source-specific qualified portion rules live in r5-qualified-source-portions.py. They preserve the exact source portion ID/hash, qualifier, quantity and gram weight. Supported qualified cups use explicit US measuring-cup/ABD ölçü kabı labels, NEVER an assumed glass/ml volume. Named cuts stay source-specific SERVING values; thin/thick slices retain the qualifier. Prepared options remain NEEDS_REVIEW, userVisible=false/defaultOption=false. Unknown qualifiers/yields/dimensions, native-basis mismatches, missing density/source-volume pairs and equivalent/conflicting old options stay held. Original IDs, labels, defaults and user history are not changed.

Example archive-only review (not fresh AWS measurement):

```powershell
python scripts/audit-r5-comparison-cycle.py `
  --usda-folder outputs/r5-source-portion-followup-20261015/usda-fresh `
  --usda-folder outputs/r5-source-portion-followup-20261015/usda-large-fresh `
  --off-folder outputs/r5-source-portion-followup-20261015/off-fresh/items `
  --archived --output outputs/r5-comparison-new-archive
```

For actual current acceptance, first renew AWS SSO, verify the current standalone-reader role/secret-reference template and run collect-r3-source-snapshot.py --scope r5-comparison with the exact selection and a NEW output directory. Then run the same audit without --archived, supplying that catalog.csv and snapshot-proof.json. Do not launch a deployment or catalog write as a substitute for missing SSO/source evidence. Once source receipts expire, fetch and review new complete provider responses through the existing bounded collectors; never copy old fetch times into new approvals.

The initial four-stage local results cover262 archived products/5502 fields, not all181k products:4362 typed SOURCE facts with old units still unproven,1122 retained unknown current values, six source inequalities and12 missing-source findings.72 qualified source measures are newly prepared (463 total source options reviewed;132 still need interpretation). Zero new nutrient/portion approvals or live-ready products are emitted. The original archived report remains unchanged; its initial SSO-wait state was subsequently resolved by the live follow-up below.

Actual AWS follow-up2026-10-15:read at19:00:50UTC /20:00:50 Europe/Dublin, schema307, exactly262 products/all5502 fields. The same four stages ran in CURRENT_EXACT_262_AWS_READONLY mode using a NEW snapshot/output directory and still-valid original source receipts. There was no fresh provider fetch or restamping. No source key or parent nutrient changed relative to the pinned historical values.20 metadata-drift products were reconciled:68 approved fields on16 products match prior name/classification/identity operations; five operational review_priority differences were retained separately, not treated as nutritional changes or new approval.

All224 old serving-option IDs and448 old localized labels are retained.13 additions/26 labels match the earlier common operation receipt;13 old options have the previously approved visibility/default retirement only. No old quantity/weight/type/label rewrite or deletion is indicated. Current option total237 is different from the historical224 because of that already-applied operation, not because this review applied new source proposals. Newly qualified72 candidates remain unapplied.

The actual307 schema has30 valid declared paths and29 dependent-content digest paths with no overflow and complete old-source-evidence coverage. The target31st path is the undeployed V310 typed ledger, so target-runtime dependency completeness stays false; do not clear this gate, deploy piecemeal or misreport it as corruption in the current catalogue. Runtime approval after the eventual consolidated schema release remains necessary.

Accepted live artifacts are under outputs/r5-comparison-cycle-20261015/aws-current/ and current-review/, with current-reconciliation.json, current-independent-proof.json, aws-cleanup-proof.json and final-live-report.json. The one0.25vCPU/512MiB readonly client task exited0 and is STOPPED; its definition is INACTIVE. API remains task91/one running instance; no catalogue write, deployment, persistent compute, cache flush or schedule change occurred. The snapshot/reports remain time-bound observations, not ongoing monitoring or proof that global R5 is complete.
