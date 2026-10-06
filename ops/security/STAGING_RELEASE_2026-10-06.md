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
- Admin production contract chain reached the bundle size check and failed there.
- Admin security session and session activity checks passed separately.
- AWS identity and ECS metadata verified read-only: service uses task definition 85,
  desired/running count one, rollout COMPLETED. This does not certify the candidate.

## Unresolved release gates

1. CVE-2026-47884 remains in Spring MVC 6.2.19. The application does not use XSLT
   views and rejects XsltView/XsltViewResolver beans, but the library is not patched.
   Official fixes require Spring 7.0.9 OSS or 6.2.20 enterprise.
   Source: https://spring.io/security/cve-2026-47884/
2. Admin lazy HEIC chunk is approximately 1.35 MB, exceeding the 750 KB check;
   CSS is approximately 371 KB, exceeding the 340 KB check. Total JS also needs
   review against its budget. The checks have not been loosened or bypassed.
3. Staging configuration parity, actual database history and snapshot, immutable
   image/source identity, migration logs, rollout and post-deploy smoke remain required.

No risk exception is approved in this record. Separate explicit staging-only
approval is required before bypassing either failed gate. Such approval must not
be treated as live-release approval or as proof that all security review work is complete.

## Rollback constraints

Keep the previous task definition and immutable image digest. Database rollback
is not equivalent to switching the image: retain an available RDS snapshot and
review backward compatibility of V299-V305 before changing the service. A restore
would lose writes after the snapshot and must not run automatically.

## Sensitive data

Do not commit local env files, AWS credentials, secret values, private media,
database dumps or raw test logs. Local evidence is under ignored outputs/ and target/.
