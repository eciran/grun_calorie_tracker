# Staging Deployment - 2026-08-13

## Backend

- Git commit: `a28bd8fa20123217e232a6a647c78893ff3427b2`
- ECR image: `911291530432.dkr.ecr.eu-west-1.amazonaws.com/grun-staging-api:staging-20260813-1434-a28bd8f`
- Image digest: `sha256:0b2ff3b6a4c80e4a26e37b0355dc977ab81fcc4e77a9bf2f3b7455514af95ac6`
- ECS task definition: `grun-staging-api-task:21`
- ECS service: `grun-staging-api-service`
- Health smoke: `https://api-staging.gruncalorietracker.com/actuator/health` returned HTTP 200.
- Admin UI smoke: `https://api-staging.gruncalorietracker.com/admin-ui/index.html` returned HTTP 200 and served `assets/index-DVjxjBD_.js`.
- Database validation: Flyway validated 205 migrations; schema version 210 was current.

## Mobile

- Mobile commit: `708083d798fd745369bae1a283f64b23368d866b`
- EAS build: `490b7bde-629b-4fd1-8182-71e34ce4d5a4`
- App version: `1.0.3 (9)`
- Platform: iOS TestFlight build.
- OCR and RevenueCat diagnostics/fixes are included in the build commit history. Real-device verification remains required.

## Push Notifications

- Staging provider: `EXPO`
- Staging delivery enabled: `GRUN_PUSH_ENABLED=true`
- Expo remains the preview/TestFlight provider for this test cycle.

Before production release, replace Expo delivery with FCM/APNs-backed production delivery:

1. Store Firebase service-account credentials in the deployment secret store, never in Git.
2. Register native provider tokens and select the FCM provider through environment configuration.
3. Verify foreground, background and terminated-state delivery on real Android and iOS devices.
4. Add token rotation, invalid-token cleanup, delivery metrics and alerting to the production gate.

## Remaining Device Gates

- OCR recognition on a physical iPhone.
- RevenueCat StoreKit product loading and sandbox purchase/restore.
- Expo push delivery while the app is foregrounded, backgrounded and terminated.
- HealthKit permission and data synchronization.
