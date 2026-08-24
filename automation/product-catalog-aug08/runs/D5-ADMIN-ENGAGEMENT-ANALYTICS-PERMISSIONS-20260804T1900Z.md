# D5 admin engagement analytics permission fixture run

- Run ID: `D5-ADMIN-ENGAGEMENT-ANALYTICS-PERMISSIONS-20260804T1900Z`
- Stage: `D5-BUILD-GATES`
- Result: `PASS_FIXTURE_FULL_SUITE_STILL_BLOCKED`
- Branch: `feature/unified-product-intake-review`
- Scope: one legacy admin permission fixture group only

## Change

Updated the admin identity in `AdminEngagementAnalyticsControllerTest` from a
legacy `ROLE_ADMIN`-only fixture to `ROLE_ADMIN` plus
`ADMIN_PERMISSION_GROWTH_READ`. The file had no pre-existing diff. Production
authorization, permission mapping, and application behavior were unchanged.

Changed-file SHA-256:

- `src/test/java/com/grun/calorietracker/controller/AdminEngagementAnalyticsControllerTest.java`
  `487E1B929CE586EA011D524A31C8C6C198B12B5870B441EB64F758899D0CAD16`

## Commands and results

```powershell
.\mvnw.cmd -Dtest=AdminEngagementAnalyticsControllerTest test
```

PASS: 2 tests, 0 failures, 0 errors, 0 skipped, including non-admin 403.

```powershell
.\mvnw.cmd test
```

FAIL (remaining contract roots): 1,466 tests, 19 failures, 0 errors, 14
skipped across 270 Surefire reports and 6 failing classes. The prior full run
had 20 failures in 7 classes; engagement analytics is cleared.

The product regression is included in the full backend run. The admin UI build
was not rerun because this backend fixture cannot affect UI assets; its recorded
PASS remains current.

## Next action

Keep `D5-BUILD-GATES` open. Inspect and isolate one single-failure expectation
root (`AdminDashboardServiceImplTest` or `MobileFoodDiaryFlowIntegrationTest`)
next. The other 17 failures sit in explicit owner-approval `denyAll()` mutation
controllers and must not be bypassed.
