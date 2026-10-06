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
3. Staging parity, database history, snapshot, image identity, migrations, rollout
   and post-deploy smoke were verified as recorded below.

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

## Deployment result: 2026-10-07 Europe/Dublin

- Source: ae563dc2 plus optimization commit 4aec14a3.
- ECR tag: staging-20261007-4aec14a3.
- Image digest: sha256:85f73dec22f23a28711bc720eab9ad6d2465359fe9c07e99d7792348cbe3ad1e.
- ECS task definition: grun-staging-api-task:86; previous revision 85 retained.
- Service rollout COMPLETED, desired/running 1, pending 0, failed tasks 0.
- Circuit breaker and automatic rollback enabled. Existing environment, secret
  references, task roles, CPU/memory and network configuration preserved.
- Runtime user explicitly 10001:10001. No WAF/Redis provisioning or mobile build.
- Read-only database preflight: V298 successful, TLSv1.3. Temporary preflight task
  exited 0 and its task definition was deregistered afterward.
- Flyway validated 300 migration files and applied V299-V305 successfully; schema V305.
- Startup completed in 174.09 seconds. No ERROR entries in checked startup/recent window.
- Health UP; anonymous users/me 401; /.env and /v3/api-docs blocked.
- All 63 deployed admin assets hash-match local packaged files; WASM content type
  application/wasm. HEIC/paywall/error-layout browser fixtures rerun successfully.
- npm audit full and omit-dev: zero reported vulnerabilities.

### Remaining acceptance limits

- The Spring configuration assessment expires 2026-10-13 UTC; production remains
  blocked until an appropriate patch/review decision. The library finding remains.
- The full V2 browser fixture needs maintenance; targeted browser checks are not
  equivalent to that full suite or real Apple/Android purchase acceptance.
- Flyway logs a PostgreSQL 18.3 support warning (this Flyway version advertises
  tested support through 17). Actual staging migrations succeeded; a supported
  Flyway/PostgreSQL compatibility upgrade is a separate production follow-up.
- One CSS request returned 404 while old/new targets overlapped during rollout;
  final stable checks passed for every asset. Versioned asset availability for
  long-lived admin tabs and mixed-target rollouts remains a production follow-up.
- Four concurrent liquid-catalog research/apply-package scripts remain untracked
  and were not executed, committed or included in the runtime image.

## Rollback constraints

Keep the previous task definition and immutable image digest. Database rollback
is not equivalent to switching the image: retain an available RDS snapshot and
review backward compatibility of V299-V305 before changing the service. A restore
would lose writes after the snapshot and must not run automatically.

## Sensitive data

Do not commit local env files, AWS credentials, secret values, private media,
database dumps or raw test logs. Local evidence is under ignored outputs/ and target/.
