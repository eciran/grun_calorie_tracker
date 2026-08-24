# D5 analytics revision mock fixture run

- Run ID: `D5-ANALYTICS-MOCKS-20260803T1053Z`
- Stage: `D5-BUILD-GATES`
- Result: `PASS_FOCUSED_FULL_SUITE_STILL_BLOCKED`
- Branch: `feature/unified-product-intake-review`
- Scope: one evidenced common root only; missing analytics cache revision mock in `ExerciseLogsServiceImplTest`

## Change

Added a Mockito `UserAnalyticsCacheRevisionService` fixture to the existing
`@InjectMocks` setup. No production source, permission mapping, application
behavior, catalog bundle, database, or network state changed. The test file had
no pre-existing diff.

Changed-file SHA-256:

- `src/test/java/com/grun/calorietracker/service/ExerciseLogsServiceImplTest.java`
  `9F0D93FD9FA03691C597CC264EAC7ED94C4B0E768C55877D9E885EFA00DAF84A`

## Commands and results

```powershell
.\mvnw.cmd -Dtest=ExerciseLogsServiceImplTest test
```

PASS: 9 tests, 0 failures, 0 errors, 0 skipped.

```powershell
.\mvnw.cmd test
```

FAIL (remaining pre-existing common roots): 1,466 tests, 32 failures, 96
errors, 14 skipped across 270 Surefire reports and 28 failing/error test
classes. The prior evidence was 1,452 tests, 32 failures, 99 errors, 14 skipped;
the three analytics-revision errors are eliminated. The focused class also
passes inside the full run: 9/0/0/0.

An initial bounded `.\mvnw.cmd test` invocation used a one-second command
timeout and was terminated before execution could complete; the immediately
repeated 180-second invocation above is the authoritative full-suite result.

The product regression and admin UI build were not rerun: the full backend run
includes the focused product tests, and this test-fixture-only change cannot
affect admin UI production assets. Their previously recorded PASS results remain
the current evidence.

## Next action

Keep `D5-BUILD-GATES` open. In the next run, isolate exactly one remaining
evidenced common root: preferably the shared H2 test-context/create-drop
collision, otherwise one legacy admin permission fixture group. Do not touch a
file with a pre-existing diff and do not change production authorization.
