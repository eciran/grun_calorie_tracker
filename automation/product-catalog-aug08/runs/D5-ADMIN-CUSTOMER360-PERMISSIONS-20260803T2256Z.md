# D5 admin customer-360 permission fixture run

- Run ID: `D5-ADMIN-CUSTOMER360-PERMISSIONS-20260803T2256Z`
- Stage: `D5-BUILD-GATES`
- Result: `PASS_FIXTURE_FULL_SUITE_STILL_BLOCKED`
- Branch: `feature/unified-product-intake-review`
- Scope: one legacy admin permission fixture group only

## Change

Updated the three admin identities in `AdminCustomer360ControllerTest` from a
legacy `ROLE_ADMIN`-only fixture to the current `ROLE_ADMIN`,
`ADMIN_PERMISSION_USERS_READ`, and `ADMIN_PERMISSION_USERS_MANAGE`
authorities. The test file had no pre-existing diff. Production authorization,
permission mapping, and application behavior were not changed.

Changed-file SHA-256:

- `src/test/java/com/grun/calorietracker/controller/AdminCustomer360ControllerTest.java`
  `3C19DCFCA9444FD18E494C3C734B72DBE2C546B30DD9DCBFA9A5ABB82444B910`

## Commands and results

```powershell
.\mvnw.cmd -Dtest=AdminCustomer360ControllerTest test
```

PASS: 3 tests, 0 failures, 0 errors, 0 skipped.

```powershell
.\mvnw.cmd test
```

FAIL (remaining fixture/contract roots): 1,466 tests, 26 failures, 0 errors,
14 skipped across 270 Surefire reports and 11 failing classes. The prior full
run had 29 failures in 12 classes, so this isolated fixture group removed all
three customer-360 failures.

The product regression is included in the full backend run. The admin UI build
was not rerun because this backend test-fixture-only change cannot affect UI
production assets; its recorded PASS remains current.

## Next action

Keep `D5-BUILD-GATES` open. Select one remaining non-`denyAll()` legacy admin
permission fixture group, preferably `AdminUserControllerTest` or
`AdminRetentionPolicyControllerTest`, and run focused plus full Maven
validation. Preserve all explicit owner-approval security gates.
