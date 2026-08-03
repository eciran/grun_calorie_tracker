# Test Feedback Program - Backend and Operations Handoff

Status: COMPLETE_DEVICE_PENDING
Date: 2026-08-03

## Scope

This system is only for authenticated Android and iOS preview/internal builds. It is not a production customer-feedback feature and it does not assign tasks to testers.

A tester can submit:
- WORKS_WELL, PROBLEM or IMPROVEMENT
- optional explanation
- optional explicitly selected screenshot
- automatically collected safe context: route, previous route, platform, build identifiers, device/OS, locale/region, coarse network state and the last HTTP status, duration and correlation ID

Request bodies, response bodies, credentials, tokens and health records are never collected automatically.

## Runtime configuration

Enable only in preview/test infrastructure with:
- GRUN_TEST_FEEDBACK_ENABLED=true
- GRUN_TEST_FEEDBACK_ALLOWED_ENVIRONMENTS=preview,test,internal
- GRUN_TEST_FEEDBACK_MAX_SUBMISSIONS_PER_MINUTE=6
- GRUN_TEST_FEEDBACK_SCREENSHOT_RETENTION_DAYS=28

Private screenshots reuse the configured S3-compatible food contribution storage. S3 must be enabled and its bucket, region and credentials must be supplied through secrets. Do not commit credentials.

Keep GRUN_TEST_FEEDBACK_ENABLED=false in production.

## API contract

Tester endpoints:
- POST /api/v1/test-feedback
- POST /api/v1/test-feedback/{id}/screenshot/upload-authorization
- POST /api/v1/test-feedback/{id}/screenshot/complete

Required headers:
- authenticated bearer token
- X-App-Environment: preview
- Idempotency-Key for submission

Admin endpoints:
- GET /api/v1/admin/test-feedback
- GET /api/v1/admin/test-feedback/{id}
- PATCH /api/v1/admin/test-feedback/{id}
- GET /api/v1/admin/test-feedback/{id}/screenshot
- GET /api/v1/admin/test-feedback/analytics
- GET /api/v1/admin/test-feedback/export

Admin review changes are audited. Screenshot read links are short lived. Stored screenshots are private and deleted after retention.

## Release gate

Automated evidence completed:
- backend compile
- focused test-feedback tests
- admin production build
- mobile TypeScript check
- mobile test-feedback contract

Device evidence still required for both Android and iOS:
1. Preview build shows the launcher after login.
2. Production profile does not show the launcher.
3. Each result type can be submitted.
4. Optional screenshot uploads and appears in admin detail.
5. Offline/failed request context is coarse and contains no payload.
6. Admin filter, pagination, detail, update, analytics and CSV export work.
7. Screenshot read URL expires and retention cleanup is observable.

The release state stays COMPLETE_DEVICE_PENDING until Android and iOS evidence is recorded.
