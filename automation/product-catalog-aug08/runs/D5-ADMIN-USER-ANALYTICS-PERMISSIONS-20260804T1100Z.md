# D5 admin user analytics permission fixture run

- Run ID: `D5-ADMIN-USER-ANALYTICS-PERMISSIONS-20260804T1100Z`
- Stage: `D5-BUILD-GATES`
- Result: `PASS_FIXTURE_FULL_SUITE_STILL_BLOCKED`
- Branch: `feature/unified-product-intake-review`
- Scope: one legacy admin permission fixture group only

## Change

Updated the admin identity in `AdminUserAnalyticsControllerTest` from a legacy
`ROLE_ADMIN`-only fixture to `ROLE_ADMIN` plus
`ADMIN_PERMISSION_USERS_READ`. The file had no pre-existing diff. Production
authorization, permission mapping, and application behavior were unchanged.

Changed-file SHA-256:

- `src/test/java/com/grun/calorietracker/controller/AdminUserAnalyticsControllerTest.java`
  `E9C2F7BF42C4EE5D8D21EF111F746387ED58E7AE6C9651AB5554ECA950E766B4`

## Commands and results

```powershell
.\mvnw.cmd -Dtest=AdminUserAnalyticsControllerTest test
```

PASS: 2 tests, 0 failures, 0 errors, 0 skipped, including non-admin 403.

```powershell
.\mvnw.cmd test
```

FAIL (remaining fixture/contract roots): 1,466 tests, 21 failures, 0 errors,
14 skipped across 270 Surefire reports and 8 failing classes. The prior full
run had 22 failures in 9 classes; the user-analytics group is cleared.

The product regression is included in the full backend run. The admin UI build
was not rerun because this backend fixture cannot affect UI assets; its recorded
PASS remains current.

## Next action

Keep `D5-BUILD-GATES` open. Isolate `AdminOnboardingAnalyticsControllerTest`
or `AdminEngagementAnalyticsControllerTest` next. Preserve explicit owner
approval `denyAll()` gates.
