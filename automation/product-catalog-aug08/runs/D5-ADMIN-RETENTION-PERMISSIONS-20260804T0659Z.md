# D5 admin retention permission fixture run

- Run ID: `D5-ADMIN-RETENTION-PERMISSIONS-20260804T0659Z`
- Stage: `D5-BUILD-GATES`
- Result: `PASS_FIXTURE_FULL_SUITE_STILL_BLOCKED`
- Branch: `feature/unified-product-intake-review`
- Scope: one legacy admin permission fixture group only

## Change

Updated the two admin identities in `AdminRetentionPolicyControllerTest` from
legacy `ROLE_ADMIN`-only fixtures to `ROLE_ADMIN`,
`ADMIN_PERMISSION_COMPLIANCE_READ`, and
`ADMIN_PERMISSION_COMPLIANCE_MANAGE`. The file had no pre-existing diff. No
production authorization, permission mapping, application behavior, catalog,
database, or network state changed.

Changed-file SHA-256:

- `src/test/java/com/grun/calorietracker/controller/AdminRetentionPolicyControllerTest.java`
  `7E57F5520C666C11A0FAB08657928EAE064D6CE5A1DDF2587E031D42FC98FF9C`

## Commands and results

```powershell
.\mvnw.cmd -Dtest=AdminRetentionPolicyControllerTest test
```

PASS: 3 tests, 0 failures, 0 errors, 0 skipped, including the explicit
non-admin 403 case.

```powershell
.\mvnw.cmd test
```

FAIL (remaining fixture/contract roots): 1,466 tests, 22 failures, 0 errors,
14 skipped across 270 Surefire reports and 9 failing classes. The prior full
run had 24 failures in 10 classes; both retention-policy failures are cleared.

The product regression is included in the full backend run. The admin UI build
was not rerun because this backend test-fixture-only change cannot affect UI
assets; its recorded PASS remains current.

## Next action

Keep `D5-BUILD-GATES` open. Isolate one of the three single-failure admin
analytics fixture groups next. Do not weaken the explicit `denyAll()` owner
approval gates in the subscription/provider/AI mutation controllers.
