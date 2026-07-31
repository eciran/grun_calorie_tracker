# Product Intake Pilot Operations Runbook

## Release rule

Production rollout is fail-closed. A stage can advance only when a real pilot evidence file passes:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\validate-product-intake-pilot.ps1 -EvidencePath <pilot-evidence.json>
```

Synthetic evidence is allowed only for validator rehearsal with `-AllowSynthetic`; it never approves production.

## Rollout stages

1. Enable internal dogfood emails with percentage `0`.
2. Select the first market and set percentage to `1`.
3. Advance monotonically through `10`, `50`, and `100` only after a passing report.
4. Add the second market through `GRUN_PRODUCT_INTAKE_ROLLOUT_MARKETS`; no code or migration change is permitted.
5. Keep `GRUN_PRODUCT_INTAKE_COHORT_SALT` unchanged during a rollout so cohort membership remains stable.

## Immediate stop conditions

Activate `GRUN_PRODUCT_INTAKE_KILL_SWITCH=true` when any of these is observed:

- internal-review product visible in user search or barcode lookup;
- unauthorized evidence access;
- deletion backlog or deletion failure;
- unexplained duplicate candidate;
- material nutrition value applied incorrectly;
- OCR crash/ANR above threshold;
- upload error, queue age, or reviewer capacity above threshold.

The kill switch blocks new contribution intake, including dogfood. Existing admin review records remain available for controlled cleanup.

## Rehearsals

- Kill switch: verify availability returns `KILL_SWITCH` and new upload/session submission is denied.
- Rollback: restore the previous rollout percentage and verify the deterministic cohort contracts without changing salt.
- Second market: append a configured `MarketRegion`, restart configuration, and verify eligibility without a build.
- Retention: run cleanup against dedicated expired evidence, verify object deletion and database deletion state.
- Authorization: verify owner and `ADMIN_CATALOG` access, and deny unrelated users, support, and read-only admins.

Store references to logs, dashboards, tickets, or test reports in the pilot evidence file. Do not place credentials, raw private images, email addresses, or signed URLs in evidence artifacts.

## Cost and capacity

Report accepted contributions, uploaded bytes, PUT/GET/DELETE operations, admin review time, and provider cost. Calculate cost per accepted contribution and compare it with `pilot-thresholds.json`. Queue p95, overdue cases, capacity utilization, and upload error rate are mandatory measurements.

## Current release gate

The reference OCR report is a parser benchmark, not device evidence. Its current decision is `STOP_ROLLOUT_VALIDATE_AND_IMPROVE_PARSER_ON_DEVICE`; Android/iOS real-device OCR evidence must replace it before market rollout.
