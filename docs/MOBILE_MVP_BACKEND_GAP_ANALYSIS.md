# Mobile MVP Backend Gap Analysis

Date: 2026-05-18

This document tracks the backend gaps that should be closed before the GRun mobile app is treated as a publishable MVP.

## Current Backend Coverage

Implemented and usable for a mobile MVP baseline:

- Auth:
  - register
  - login
  - email verification
  - password reset
- User profile:
  - current user profile
  - profile update
  - BMI/body fat calculation
- Food catalog:
  - product search
  - barcode lookup
  - local DB first, Open Food Facts fallback second
  - product quality/review metadata
- Food logging:
  - create food log
  - list food logs
  - get/delete own food log
  - daily nutrition stats
- Exercise:
  - exercise item catalog
  - manual exercise logs
  - external exercise logs with duplicate protection
  - date range report
- Dashboard:
  - daily summary
- Admin:
  - user list
  - dashboard summary
  - food product review queue
  - duplicate product analysis and merge
  - product review audit history
- Localization:
  - validation and error category messages for English and Turkish.

## MVP Blocking Gaps

These should be resolved before mobile publish.

1. Real email provider integration
   - Current email verification and password reset use local logging sender behavior.
   - Need provider implementation behind current sender interfaces.
   - Candidate providers: Brevo, Resend, Amazon SES.

2. Mobile-friendly auth response policy
   - Register currently returns no JWT until email verification.
   - Mobile UX must define whether unverified users can enter a limited onboarding screen.
   - Backend should expose a clear email verification status in profile/auth response if needed.

3. Refresh token / session lifecycle
   - Current JWT flow is basic.
   - Mobile app needs refresh token, logout/revoke strategy, and token expiry policy.

4. API versioning
   - Current endpoints are under `/api/...`.
   - Before public mobile release, decide whether MVP should start with `/api/v1/...`.
   - If not changed now, document that breaking changes can still happen during private beta.

5. Food log portion model
   - `portionSize` exists, but serving unit semantics are not fully explicit.
   - Mobile UX needs clear units such as gram, serving, piece, ml, and user-entered quantity.
   - Backend likely needs serving unit fields before real users rely on nutrition totals.

6. Food catalog media handling
   - `displayImageUrl` and image review status exist.
   - Missing: upload/storage flow for curated product images.
   - Need CDN/object storage decision and admin upload API or admin UI integration.

7. Admin operational workflow
   - Admin APIs exist, but admin UI is not part of backend.
   - For MVP operations, Swagger/admin API may be enough for internal beta.
   - For public launch, a simple admin panel is recommended.

8. Observability and production safety
   - Need structured logs, request tracing/correlation id, production profile config, and safe error detail policy.
   - Need rate limiting for auth, password reset, email verification, barcode lookup, and Open Food Facts fallback.

9. Mobile app data sync behavior
   - Need pagination consistency for list endpoints.
   - Need date filtering on progress logs and possibly food/exercise logs beyond current basic support.
   - Need clear empty-state responses for dashboard and logs.

10. Seed/import strategy for production
    - Local demo seed is useful only for development.
    - Production should start with minimal verified catalog data and grow via lookup/cache/admin review.
    - Bulk import can be added later, but must not bypass quality status.

## Important Non-Blocking Gaps

These can wait until after a controlled MVP/beta.

- AI Workout Planner.
- Workout plan persistence.
- Exercise movement preview video/gif enrichment at scale.
- Subscription/payment system.
- Push notifications.
- Wearable integrations beyond external exercise log API.
- Advanced food recommendation and meal planning.

## Suggested Next Backend Sprint

Recommended order:

1. Add refresh token and logout/revoke flow.
2. Add real email sender implementation behind the existing interfaces.
3. Add explicit food portion/unit model.
4. Add rate limiting for auth and external food lookup endpoints.
5. Add API versioning decision and document mobile contract.

## Decision Notes

- The current backend is strong enough for local Swagger testing and early mobile client integration.
- It is not yet production/publish ready because email delivery, token lifecycle, rate limiting, and portion semantics are still incomplete.
- Food catalog architecture is directionally correct: own DB is primary, Open Food Facts is fallback/cache source, admin review controls quality.
