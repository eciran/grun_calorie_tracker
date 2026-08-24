# Product Intake Private Storage Operations Runbook

## Scope

This runbook covers the private, S3-compatible object store used for temporary product-intake evidence. It is provider-neutral: endpoint, region, jurisdiction, bucket, prefixes and browser origins are deployment metadata. No market, country or locale is encoded in storage rules.

## Required invariants

- The bucket is private; anonymous object listing and reads are disabled.
- The application receives credentials limited to object read/write/delete for the configured prefixes and bucket-policy inspection where supported.
- Browsers upload only through short-lived signed PUT URLs. Admin preview uses short-lived signed GET URLs.
- CORS origins are explicit. Wildcard origins are forbidden.
- `pending/product-intakes/` expires no later than 30 days.
- reviewed evidence expires no later than 90 days; application cleanup remains the primary deletion path and lifecycle is the hard backstop.
- Incomplete multipart uploads are aborted within one day.
- Server-side encryption and provider audit logging are enabled when the provider exposes them.

## Deployment inputs

Replace the example origins in `ops/product-intake-storage/cors-policy.json`. If deployment uses a different configured prefix, update lifecycle prefixes to match `GRUN_FOOD_CONTRIBUTION_S3_PREFIX`. Keep retention values at or below the application configuration.

Never commit bucket credentials. Configure these through the deployment secret store:

- `GRUN_FOOD_CONTRIBUTION_STORAGE_PROVIDER=S3`
- `GRUN_FOOD_CONTRIBUTION_S3_ENDPOINT`
- `GRUN_FOOD_CONTRIBUTION_S3_JURISDICTION`
- `GRUN_FOOD_CONTRIBUTION_S3_ACCESS_KEY`
- `GRUN_FOOD_CONTRIBUTION_S3_SECRET_KEY`
- `GRUN_FOOD_CONTRIBUTION_S3_BUCKET`
- `GRUN_FOOD_CONTRIBUTION_S3_REGION`
- `GRUN_FOOD_CONTRIBUTION_S3_PREFIX`

## Preflight

Run without provider credentials:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\validate-product-intake-storage-policy.ps1
```

The command must return `status=VALID`, pending retention at most 30 days and evidence retention at most 90 days.

## Provider application

Translate the checked-in neutral policies to the selected provider's lifecycle and CORS API. Before applying, export the provider's current configuration to an access-controlled change artifact. Apply first in staging. Production mutation is intentionally not automated from this repository.

Record in the release evidence:

- provider and jurisdiction (never credentials);
- bucket identifier or approved alias;
- policy version or checksum;
- applied timestamp and operator/change ticket;
- provider API output showing private access, CORS and lifecycle rules;
- rollback artifact location.

## Staging verification

1. Confirm anonymous HEAD/GET/list requests fail.
2. Create an upload session through the backend and verify exactly two signed PUT slots.
3. Upload allowed images from every configured browser origin; verify an unlisted origin is denied.
4. Verify PUT after URL expiry fails.
5. Finalize and verify checksum, MIME signature and decoded dimensions are enforced.
6. Confirm `ADMIN_READ_ONLY` cannot obtain a signed read URL; OWNER and ADMIN_CATALOG can obtain a short-lived URL.
7. Withdraw one case, run cleanup, and confirm the object is absent and the database deletion state is `DELETED`.
8. Seed an already-expired staging object under each prefix or temporarily use a provider-supported short lifecycle in a disposable prefix. Capture deletion evidence, then restore the approved policy.
9. Query for active evidence older than 90 days; the count must be zero. Repeat after two cleanup intervals.
10. Run the focused backend test suite and archive its result with the provider evidence.

## Rollback

1. Disable new intake uploads with `GRUN_FOOD_CONTRIBUTION_STORAGE_PROVIDER=LOCAL` only in non-production, or with the deployment feature switch used by production. Do not expose the bucket publicly.
2. Restore the previously exported CORS/lifecycle configuration.
3. Revoke the newly introduced storage credential and deploy the previous secret version if credentials changed.
4. Preserve database records and deletion states; do not bulk-delete evidence during rollback.
5. Re-run privacy, signed-read and retention checks before reopening intake.

## Ongoing evidence

At least daily, alert on cleanup failures and verify no active private evidence is older than 90 days. At each release, record upload/finalize/evidence authorization success/failure counters, cleanup deleted/failed counters, storage size/object count and estimated monthly cost. Provider-side lifecycle evidence must be refreshed after any prefix, origin, retention or jurisdiction change.