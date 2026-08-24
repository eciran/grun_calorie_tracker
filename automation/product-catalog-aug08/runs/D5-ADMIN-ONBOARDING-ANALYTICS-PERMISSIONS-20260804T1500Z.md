# D5 admin onboarding analytics permission fixture run

- Run ID: `D5-ADMIN-ONBOARDING-ANALYTICS-PERMISSIONS-20260804T1500Z`
- Stage: `D5-BUILD-GATES`
- Result: `PASS_FIXTURE_FULL_SUITE_STILL_BLOCKED`
- Branch: `feature/unified-product-intake-review`
- Scope: one legacy admin permission fixture group only

## Change

Updated the admin identity in `AdminOnboardingAnalyticsControllerTest` from a
legacy `ROLE_ADMIN`-only fixture to `ROLE_ADMIN` plus
`ADMIN_PERMISSION_GROWTH_READ`. The file had no pre-existing diff. Production
authorization, permission mapping, and application behavior were unchanged.

Changed-file SHA-256:

- `src/test/java/com/grun/calorietracker/controller/AdminOnboardingAnalyticsControllerTest.java`
  `BA78069BAC8940A29B35E17C383530B74E0DC2C04F1B5E8C9BF8BB3144AEE519`

## Commands and results

```powershell
.\mvnw.cmd -Dtest=AdminOnboardingAnalyticsControllerTest test
```

PASS: 2 tests, 0 failures, 0 errors, 0 skipped, including non-admin 403.

```powershell
.\mvnw.cmd test
```

FAIL (remaining fixture/contract roots): 1,466 tests, 20 failures, 0 errors,
14 skipped across 270 Surefire reports and 7 failing classes. The prior full
run had 21 failures in 8 classes; onboarding analytics is cleared.

The product regression is included in the full backend run. The admin UI build
was not rerun because this backend fixture cannot affect UI assets; its recorded
PASS remains current.

## Next action

Keep `D5-BUILD-GATES` open. Isolate
`AdminEngagementAnalyticsControllerTest` next. Preserve explicit owner-approval
`denyAll()` gates.
