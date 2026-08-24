# D5 admin dashboard expectation fixture run

- Run ID: `D5-ADMIN-DASHBOARD-EXPECTATION-20260804T2300Z`
- Stage: `D5-BUILD-GATES`
- Result: `PASS_FIXTURE_FULL_SUITE_STILL_BLOCKED`
- Branch: `feature/unified-product-intake-review`
- Scope: one evidenced stale test expectation only

## Change

Updated `AdminDashboardServiceImplTest` to stub the current all-admin-role
repository query (`countByRoleIn`) instead of the obsolete single `ADMIN` role
query. The file had no pre-existing diff. Production code, authorization,
permission mapping, application behavior, catalog, database, and network state
were unchanged.

Changed-file SHA-256:

- `src/test/java/com/grun/calorietracker/service/AdminDashboardServiceImplTest.java`
  `C40EBC51D224F241C3C1DD0B167CCE53EA3F70CBD5E91399188B29C57957A420`

## Commands and results

```powershell
.\mvnw.cmd -Dtest=AdminDashboardServiceImplTest test
```

PASS: 1 test, 0 failures, 0 errors, 0 skipped.

```powershell
.\mvnw.cmd test
```

FAIL (remaining contract roots): 1,466 tests, 18 failures, 0 errors, 14
skipped across 270 Surefire reports and 5 failing classes. The prior full run
had 19 failures in 6 classes; the dashboard expectation is cleared.

The product regression is included in the full backend run. The admin UI build
was not rerun because this backend test fixture cannot affect UI assets; its
recorded PASS remains current.

## Next action

Keep `D5-BUILD-GATES` open. Isolate the final non-owner-gate failure in
`MobileFoodDiaryFlowIntegrationTest`. The other 17 failures remain in explicit
owner-approval `denyAll()` mutation controllers and must not be bypassed.
