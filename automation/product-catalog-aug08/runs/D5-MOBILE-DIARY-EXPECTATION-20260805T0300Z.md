# D5 mobile diary flow diagnosis

- Run ID: `D5-MOBILE-DIARY-EXPECTATION-20260805T0300Z`
- Stage: `D5-BUILD-GATES`
- Result: `BLOCKED_APPLICATION_BEHAVIOR`
- Branch: `feature/unified-product-intake-review`
- Scope: final non-owner-gate failing class only

## Evidence and bounded attempts

The existing full-suite report failed onboarding with H2 rejecting the
PostgreSQL-specific analytics revision `ON CONFLICT` statement.

```powershell
.\mvnw.cmd -Dtest=MobileFoodDiaryFlowIntegrationTest test
```

A bounded test-only experiment mocked `UserAnalyticsCacheRevisionService`.
Onboarding then passed, but the flow reached a second independent failure:
the template-applied food log returned `snapshotFiber: null` (and other optional
micro snapshots null) while the source manual log correctly contained
`snapshotFiber: 8.0` and `snapshotSodium: 96.0`.

A second bounded fixture experiment used `saveAndFlush` after adding test
micronutrients. The same template snapshot failure remained. Both experiments
were fully reverted because the focused test did not pass. The test file is
byte-for-byte back to its pre-run Git state; no production file was changed.

Evidence hashes:

- Restored test file SHA-256:
  `8FDBF7D5353DBFF8B049835A8EF5AB2716E49E007F34F1A7EADD5EBA8701BD09`
- Latest focused Surefire XML SHA-256:
  `1F6985B4A28DA415B1B80F94D61CE7AE32CF4CA88BBCEB86234F63B9D2419A9E`

No final source/test modification was retained, so the full Maven suite was not
rerun. The authoritative prior full-suite state remains 1,466 tests, 18
failures, 0 errors, 14 skipped.

## Blocker and next action

The remaining mobile-flow failure is not safely fixable as a fixture-only
change: it exposes production template-application behavior that does not carry
optional nutrition snapshots in this scenario. The unattended run is forbidden
from changing application behavior merely to make the suite pass.

Owner action is required to decide whether template application must preserve
the source log's optional nutrition snapshot or intentionally recalculate from
current food data. After that product contract is chosen, implement and test it
in an authorized application-behavior task. D5 remains open; the other 17
failures are explicit owner-approval `denyAll()` contract mismatches.
