# D5 admin UI build monitor — 2026-08-05 10:00 Europe/Dublin

- Run ID: `D5-ADMIN-UI-MONITOR-20260805T1100Z`
- Branch: `feature/unified-product-intake-review`
- Stage: `D5-BUILD-GATES`
- Result: `PASS_ADMIN_UI_BUILD_D5_STILL_BLOCKED`

## Commands

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\product-catalog-aug08-automation-lock.ps1 -Action Acquire -RunId D5-ADMIN-UI-MONITOR-20260805T1100Z
Push-Location .\admin-ui
npm run build
Pop-Location
powershell -ExecutionPolicy Bypass -File .\scripts\product-catalog-aug08-automation-lock.ps1 -Action Release -RunId D5-ADMIN-UI-MONITOR-20260805T1100Z
git status --short -- src/main/resources/static/admin-ui
Get-ChildItem -LiteralPath src/main/resources/static/admin-ui -File -Recurse
Get-FileHash -Algorithm SHA256 -LiteralPath <each-admin-ui-output>
```

The first sandboxed build attempt reached Vite but failed to spawn esbuild with
`EPERM`. It was rerun outside the restricted sandbox after approval; the lock
was acquired and released on both effective attempts.

## Evidence

- TypeScript/Vite production build: PASS
- Vite: 6.4.3
- Modules transformed: 634
- Build time: 4.63 seconds
- Output files: 25
- Output bytes: 1,711,802
- `index.html` SHA-256: `89ED9DCD61E69CEE60E8FDE0B7E8114C3691593CB921720D58AA2537F9C2216E`
- Main JavaScript SHA-256: `85AC9D119AD8A20B94EC779041A2DEFC78C4C39342D3B1A81962328E008A45D1`
- Main CSS SHA-256: `BED78FE89B8F6CDABE38D81FEB8B5B457768C880BAE60D585EBD3DB20676F32C`
- Build-output working-tree diff: none
- Warning only: two minified chunks exceed 500 kB; this does not fail the production build.
- Unrelated dirty files were preserved.

## Blocker and next action

Admin UI remains green. D5 remains blocked by the already-evidenced product-owner
contracts: template-applied optional nutrition snapshot preservation versus
recalculation, and 17 explicit `denyAll()` admin mutation expectations. No
application behavior, production permission mapping, test fixture, catalog, or
database was changed. After owner direction, implement only the chosen contract
and run focused tests, the full Maven suite, product regression, build, and final
release-candidate manifest.
