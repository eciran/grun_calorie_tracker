# Combined staging release candidate

## Scope

- Backend and embedded admin UI changes in this repository, including migrations V299-V305.
- Goal direction and independent muscle focus, purchase verification and diagnostics,
  admin MFA, credential revocation, ownership checks, request limits, media guards,
  serving options, product quality review and dependency/container hardening.
- Existing commits after deployed source 08ce2259 are included in the candidate.
- Mobile application sources require a separate native release; they are not deployed by ECS.
- WAF and managed Redis provisioning are explicitly deferred by the user. Redis remains
  disabled in staging; fallback rate limits are process-local, not globally shared.

## Verification performed

- Admin TypeScript and production build succeeded; the embedded static bundle was regenerated.
- Backend selected regression group: 337 scenarios, zero failures/errors, seven skipped.
- Disposable PostgreSQL 16.13 acceptance rerun: six tests passed, zero skipped;
  300 migration files validated and schema reached V305. No existing database was modified.
- Admin production contract chain passed after the optimization below.
- Admin security session and session activity checks passed separately.
- AWS identity and ECS metadata verified read-only: service uses task definition 85,
  desired/running count one, rollout COMPLETED. This does not certify the candidate.

## Release assessment

1. CVE-2026-47884 remains in Spring MVC 6.2.19. The application does not use XSLT
   views and rejects XsltView/XsltViewResolver beans, but the library is not patched.
   Official fixes require Spring 7.0.9 OSS or 6.2.20 enterprise.
   Source: https://spring.io/security/cve-2026-47884/
2. The original HEIC/CSS size failures were resolved without raising existing budgets.
3. Staging configuration parity, actual database history and snapshot, immutable
   image/source identity, migration logs, rollout and post-deploy smoke remain required.

## Follow-up assessment and optimization

The user requested the recommended reachability assessment and optimizations before
commit/deployment. The exact Spring MVC finding is retained in scanner reports and
evaluated as not affected by current staging configuration, not as a patched library.
All application handler methods are checked for ResponseBody/HttpEntity handling;
the two XSLT guard tests also pass. The assessment expires on 2026-10-13 UTC and
is not accepted for production. The artifact gate binds evidence to application
class hashes inside the scanned JAR. Any other high/critical finding still blocks.

Admin HEIC uses libheif-js 1.23.5 in a short-lived, serialized worker with a separate
on-demand WASM asset. Normal images do not load the decoder. Original uploads are
unchanged. A public libheif sample converted successfully in Chrome (1280x854,
about 241 ms); malformed and >25 MiB files were rejected and subsequent work recovered.
The worker limits decoded images to 50 million pixels and conversions to 60 seconds.
Mail, product-review and owner-error CSS is page-loaded; cssnano removes safe redundancy.
Existing entry/JS/CSS thresholds are unchanged. Production checks now pass:
entry JS 299205 bytes, total JS 2341901 bytes, entry CSS 332102 bytes.
The optional WASM asset has a separate 2 MB budget and no initial-shell preload.
MFA and paywall browser fixtures passed on desktop/mobile. These are local fixtures,
not proof of real store purchase or real device behavior.
The owner-error lazy stylesheet smoke also passed at desktop/mobile sizes. Shared
selectors remain global; selector/declaration/media inventory matches the baseline.
The general V2 browser fixture still contains stale barcode-page expectations and
does not pass as a whole. This is a remaining test-maintenance task, not a passing
end-to-end claim. The focused checks do not replace the full browser suite.

RDS snapshot grun-staging-pre-security-20261006 is available. The final runtime JAR
hash matches the Linux build JAR and its tested application classes. Three unrelated
liquid-catalog research scripts appeared concurrently and are excluded from this
release commit; they do not alter the packaged application.

## Rollback constraints

Keep the previous task definition and immutable image digest. Database rollback
is not equivalent to switching the image: retain an available RDS snapshot and
review backward compatibility of V299-V305 before changing the service. A restore
would lose writes after the snapshot and must not run automatically.

## Sensitive data

Do not commit local env files, AWS credentials, secret values, private media,
database dumps or raw test logs. Local evidence is under ignored outputs/ and target/.
