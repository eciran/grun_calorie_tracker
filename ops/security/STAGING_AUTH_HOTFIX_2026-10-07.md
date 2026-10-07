# Staging mobile authentication hotfix

## Evidence

- Login correlation `lwlg50fmq` returned HTTP 200 in 151 ms.
- The following startup request `f1b05depn` took 30118 ms. Its optional
  activity recording could not acquire a Hikari connection (10 active, zero idle).
- The installed mobile client's request timeout is 20 seconds. A late server 200
  therefore does not prove that the client received the startup response.
- Read-only PostgreSQL inspection found a refresh-token transaction idle for
  1164 seconds, with user-row lock waiters queued behind it.
- Refresh holds a pessimistic user lock, then calls activity recording in
  REQUIRES_NEW. The inner transaction waits for the outer transaction's own lock;
  the outer transaction waits for the inner call. Repeated attempts exhaust the pool.

## Change

- Commit: `d0a3741d`.
- Register activity recording after successful refresh commit, releasing the
  user lock before the separate activity transaction starts.
- Rollback does not record refresh activity. Activity errors remain non-fatal.
- Keep refresh rotation, atomic consumption, replay protection and user-lock
  security checks unchanged.
- Replace the activity mock in PostgreSQL concurrency tests with the real service.
- Add a bounded real-database regression test and commit/rollback callback tests.

## Validation

- Isolated release source: deployment HEAD plus only the three hotfix files.
- 32 selected tests passed, zero failures/errors/skips: refresh unit tests,
  startup service tests, real PostgreSQL concurrency tests and XSLT guard tests.
- PostgreSQL test database: disposable PostgreSQL 16; container removed afterward.
- Release JAR security gate passed under the existing staging-only assessment.
  CVE-2026-47884 remains in the library inventory; assessment expires October 13 UTC.
  This release does not resolve that library finding or authorize production.
- Runtime inherits the exact previous image. Its deployed admin/config resources
  were preserved byte-for-byte; all 228 resource/dependency entries match.
- Tested application classes are bound to the final JAR by the security gate.
- Runtime user remains 10001:10001. No schema, secret, schedule, admin or catalog changes.

## Deployment

- ECR tag: `staging-20261007-auth-d0a3741d`.
- Image: `sha256:d4fd2b40209093f6fe6a9b34a36eeab688e6361edd0d68cff2602ed81c1aede8`.
- ECS task definition: `grun-staging-api-task:87`; previous revision 86 retained.
- Rollout: COMPLETED; desired/running 1, pending 0, failed tasks 0.
  Circuit breaker and automatic rollback retained; previous task is stopped.
- Application started successfully in 170 seconds. Schema V305 is unchanged;
  300 migrations validated and none applied.
- Health endpoint returned 200/UP; admin returned 200; anonymous startup returned
  the expected 401. These are infrastructure checks, not authenticated device tests.
- Post-deploy read-only database probe: no idle transaction, zero blocked sessions.
- Temporary probe tasks exited successfully and their definitions were deregistered.

## Acceptance Still Required

- On the actual installed mobile build, sign in, open the dashboard and profile,
  then exercise access-token expiry/refresh and concurrent authenticated requests.
- Verify test1 and another account without changing their roles or subscription state.
- General 401 logs alone do not identify the rejection reason. The refresh deadlock
  is proven, but not every earlier 401 has been individually attributed to it.
- Mobile generic network/timeout copy and unauthenticated profile query gating
  remain separate follow-ups, excluded from this backend-only hotfix.

Raw logs and artifact evidence remain in ignored `outputs/security-review/`.
