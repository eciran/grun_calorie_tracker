# D5 admin subscription permission fixture run

- Run ID: `D5-ADMIN-SUBSCRIPTION-PERMISSIONS-20260803T1855Z`
- Stage: `D5-BUILD-GATES`
- Result: `PASS_PARTIAL_FIXTURE_FULL_SUITE_STILL_BLOCKED`
- Branch: `feature/unified-product-intake-review`
- Scope: one legacy admin permission fixture group only

## Change

Updated the seven admin identities in `AdminSubscriptionControllerTest` from a
legacy `ROLE_ADMIN`-only fixture to the current `ROLE_ADMIN`,
`ADMIN_PERMISSION_FINANCE_READ`, and `ADMIN_PERMISSION_FINANCE_MANAGE`
authorities. The test file had no pre-existing diff. Production permission
mapping and application behavior were not changed.

Changed-file SHA-256:

- `src/test/java/com/grun/calorietracker/controller/AdminSubscriptionControllerTest.java`
  `2C475D89A572F6F2C016A930A61C9FC2FF863E539A8EAF80410AC2F46AAD12A3`

## Commands and results

```powershell
.\mvnw.cmd -Dtest=AdminSubscriptionControllerTest test
```

PARTIAL PASS: 9 tests, 5 pass, 4 fail, 0 errors. Three stale permission
failures were eliminated: the two finance-read requests and the invalid-input
request now reach their expected controller/validation behavior. Four
owner-approval-sensitive mutation endpoints remain 403 because production
explicitly applies `@PreAuthorize("denyAll()")`; this run did not weaken that
security boundary or rewrite the tests to hide it.

```powershell
.\mvnw.cmd test
```

FAIL (remaining roots): 1,466 tests, 29 failures, 0 errors, 14 skipped across
270 Surefire reports and 12 failing classes. This improves the prior full run
from 32 to 29 failures while preserving the explicit owner-approval gates.

The product regression is included in the full backend run. The admin UI build
was not rerun because this backend test-fixture-only change cannot affect UI
production assets; its recorded PASS remains current.

## Next action

Keep `D5-BUILD-GATES` open. Select one remaining legacy admin permission fixture
group whose endpoint is not explicitly `denyAll()`, add only its current domain
authorities, and run focused plus full Maven validation. Treat the four
subscription mutations as owner-approval/security-contract blockers; never
change production authorization merely to make them green.
