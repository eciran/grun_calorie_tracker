# D5 read-only bundle preflight — 2026-08-05 06:00 Europe/Dublin

- Run ID: `D5-READONLY-PREFLIGHT-20260805T0700Z`
- Branch: `feature/unified-product-intake-review`
- Stage: `D5-BUILD-GATES`
- Result: `BLOCKED_OWNER_DECISION`

## Commands

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\product-catalog-aug08-automation-lock.ps1 -Action Acquire -RunId D5-READONLY-PREFLIGHT-20260805T0700Z
Get-FileHash -Algorithm SHA256 -LiteralPath outputs/product-catalog-aug08/licensed-default-20260802/bundle-manifest.json
powershell -ExecutionPolicy Bypass -File .\scripts\import-product-catalog-aug08-bundle.ps1
```

## Evidence

- Preflight: `PREFLIGHT_PASS`
- Release ID: `product-catalog-aug08-licensed-default-20260802`
- Manifest SHA-256: `150B70D622473FF4C1406427678B3CB7939B33FA3795383451F6451BA5AB4C4D`
- Hash-verified chunks: 21
- Input rows: 176,631
- Expected canonical products: 174,581
- Expected cross-market merges: 2,050
- Market rows: UK/IE 151,928; EU 22,962; TR 1,595; global generic 146
- Database mutation: none (preflight only)
- Licensed-default and `PRIVATE_TEST_ONLY` overlays remained separate.
- Unrelated working-tree changes were preserved.

## Blocker and next action

The bundle remains intact, but D5 cannot become green without product-owner decisions for the remaining test contracts: whether template application preserves optional nutrition snapshots or recalculates them from current food data, and whether the 17 explicit `denyAll()` admin mutations should remain forbidden or receive reviewed production permission mappings. No fixture or application change is safe until those contracts are chosen.

After owner direction, implement only the selected contract, run its focused test, the full Maven suite, product regression, admin UI production build, and regenerate the release-candidate manifest. Until then, future runs should use bounded read-only integrity checks and must not alter application behavior.
