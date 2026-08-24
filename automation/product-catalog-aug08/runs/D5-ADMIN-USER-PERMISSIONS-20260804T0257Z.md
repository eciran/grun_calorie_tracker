# D5 admin user permission fixture run

- Run ID: `D5-ADMIN-USER-PERMISSIONS-20260804T0257Z`
- Stage: `D5-BUILD-GATES`
- Result: `PASS_FIXTURE_FULL_SUITE_STILL_BLOCKED`
- Branch: `feature/unified-product-intake-review`
- Scope: one legacy admin permission fixture group only

## Change

Updated the two admin identities in `AdminUserControllerTest` from a legacy
`ROLE_ADMIN`-only fixture to `ROLE_ADMIN`, `ADMIN_PERMISSION_USERS_READ`, and
`ADMIN_PERMISSION_USERS_MANAGE`. The file had no pre-existing diff. No
production permission mapping, application behavior, catalog, database, or
network state changed.

Changed-file SHA-256:

- `src/test/java/com/grun/calorietracker/controller/AdminUserControllerTest.java`
  `0433FFF3343C698DE90CF3079176090676E7387DC9843D1887EF8D888B713F90`

## Commands and results

```powershell
.\mvnw.cmd -Dtest=AdminUserControllerTest test
```

PASS: 3 tests, 0 failures, 0 errors, 0 skipped, including the explicit
non-admin 403 case.

```powershell
.\mvnw.cmd test
```

FAIL (remaining fixture/contract roots): 1,466 tests, 24 failures, 0 errors,
14 skipped across 270 Surefire reports and 10 failing classes. The prior full
run had 26 failures in 11 classes; both admin-user failures are cleared.

The product regression is included in the full backend run. The admin UI build
was not rerun because this backend test-fixture-only change cannot affect UI
assets; its recorded PASS remains current.

## Next action

Keep `D5-BUILD-GATES` open. Isolate `AdminRetentionPolicyControllerTest` as the
next non-`denyAll()` legacy permission fixture group and run focused plus full
Maven validation. Preserve all owner-approval gates.
