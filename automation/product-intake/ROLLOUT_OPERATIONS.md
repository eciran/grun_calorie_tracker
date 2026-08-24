# Product Intake rollout operations

Product Intake rollout is market-neutral and fail-closed. A market is enabled
only through configuration; no market name participates in an application
business rule.

## Staged rollout

Set the following runtime configuration:

```text
GRUN_PRODUCT_INTAKE_ROLLOUT_ENABLED=true
GRUN_PRODUCT_INTAKE_ROLLOUT_PERCENTAGE=1
GRUN_PRODUCT_INTAKE_ROLLOUT_MARKETS=EU
```

The accepted rollout stages are `0`, `1`, `10`, `50`, and `100`. Cohort
membership is deterministic, so increasing the percentage retains every
previously eligible user. `0` disables the public cohort while still allowing
configured dogfood users.

Advance one stage at a time only after the current pilot metrics and operational
gates are accepted.

## Add another market

Append the enum-backed market identifier to the same configuration:

```text
GRUN_PRODUCT_INTAKE_ROLLOUT_MARKETS=EU,UK_IE
```

No backend implementation or country-specific condition is required. Users
outside the configured markets remain in the existing manual search and Custom
Food paths.

## Emergency stop

Set `GRUN_PRODUCT_INTAKE_KILL_SWITCH=true`. The kill switch takes precedence
over market, cohort, and internal dogfood eligibility.
## Pilot quality gate

Copy `ops/product-intake-pilot/pilot-evidence.template.json` to an evidence
archive, populate it only from a real versioned OCR corpus and operational
measurements, then run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/validate-product-intake-pilot.ps1 `
  -EvidencePath <real-pilot-evidence.json>
```

The validator checks two measured languages with at least 50 labels each,
critical-field safety, latency/crash thresholds, admin queue SLA/capacity,
retention backlog and provider proof. `-AllowDeferredProviderProof` is for local
preflight only and must not be used for production approval.