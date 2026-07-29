# Grocery List MVP Plan and Hold Report

Date: 2026-07-28
Status: IN PROGRESS
Implementation decision: No code or UI change until the product owner resumes this work.

## 1. Product purpose

Grocery List converts one owned meal plan into consolidated shopping quantities. Direct food items are aggregated and recipe items are expanded into ingredients. Its user value is reducing manual calculation between meal planning and actual shopping.

This is a planning workflow, not another food diary screen. The primary navigation remains:

`Meal Plans -> Plan Detail -> Grocery List`

A standalone main-tab entry is not recommended for MVP.

## 2. Current implementation

Backend currently provides:

- `GET /api/v1/meal-plans/{planId}/grocery-list`;
- ownership validation for the requested meal plan;
- aggregation by `foodItemId`;
- expansion of recipe ingredients;
- shared portion conversion for gram, serving and piece quantities;
- fallback to grams when mixed units are aggregated.

Mobile currently provides:

- `mealPlanService.getGroceryList(planId)`;
- a Grocery List action in meal-plan detail;
- a read-only bottom sheet showing amount, approximate grams and planned use count.

The current backend response is generated on demand. It is not a persisted, editable shopping checklist.

## 3. Identified gap and security decision

There is no dedicated `GROCERY_LIST` subscription feature today. The endpoint is authenticated and ownership-safe, but it does not enforce a paid entitlement. A client-side feature gate alone is not sufficient because a FREE user could call the endpoint directly.

Do not bind Grocery List to `AI_NUTRITION_PLAN`:

- grocery data can come from manual meal plans;
- recipes and catalog foods are valid non-AI sources;
- commercial access must remain independently configurable in Admin Feature Matrix.

Introduce a dedicated `GROCERY_LIST` entitlement and enforce it in the backend.

Recommended initial matrix:

| Plan | Grocery List |
|---|---|
| FREE | Disabled |
| PLUS | Enabled |
| PRO | Enabled |

PLUS and PRO should receive the same Grocery List MVP. Do not create an artificial Pro limitation before a genuinely advanced capability exists. Future pantry intelligence, retailer integration, price optimization or AI substitutions may become separate Pro features.

Existing entitlement snapshot policy must apply: a paid user keeps granted access until the current subscription period ends unless an administrator explicitly applies a matrix change immediately through the existing confirmed action.

## 4. Target MVP

The target MVP is a persisted shopping workflow layered over the existing aggregation engine:

- create or refresh a grocery list from one meal plan;
- retain the source meal-plan id and source revision/update timestamp;
- mark items purchased/unpurchased;
- add a manual item;
- exclude an automatically generated item;
- override display quantity without mutating the meal plan;
- group items into practical categories;
- retain progress when the user closes and reopens the app;
- warn when the source meal plan changed and offer an explicit refresh/merge;
- preserve ownership and subscription checks on every read/write operation.

Out of MVP:

- retailer checkout and delivery integrations;
- live prices and store inventory;
- household collaboration and real-time shared lists;
- pantry stock deduction;
- AI brand selection, price optimization or substitutions.

## 5. Implementation progress

| Sprint | Status | Verification |
|---|---|---|
| Sprint 1 - Entitlement foundation | COMPLETED (2026-07-29) | 57 targeted tests passed; FREE denied, PLUS/PRO enabled, standard 403 and service-level defense added |
| Sprint 2 - Persisted grocery domain | COMPLETED (2026-07-29) | Persisted list/item entities, ownership repositories, optimistic locking, bounded fields and V197 migration; 5 targeted tests passed |
| Sprint 3 - Grocery API | COMPLETED (2026-07-29) | Persistent create/get, manual item, purchased toggle, quantity override, remove/exclude, complete/archive endpoints; 14 targeted tests passed |
| Sprint 4 - Refresh and merge | COMPLETED (2026-07-29) | Source freshness metadata, explicit deterministic refresh, preserved user decisions, source-removed history, optimistic conflict protection; 17 targeted tests passed |
| Sprint 5 - Mobile integration handoff | COMPLETED (2026-07-29) | Dedicated mobile contract covers entitlement gating, API/DTO models, refresh merge UX, cache replacement, 409 recovery, screen states and acceptance criteria; approved UI remains unchanged |
| Sprint 6 - Quality and observability | COMPLETED (2026-07-29) | Privacy-safe aggregate Micrometer operations, deterministic lock-order coverage, serving/piece/mixed-unit regressions and full grocery security/domain/migration suite; 33 targeted tests passed |

## 6. Planned sprints

### Sprint 1 - Entitlement foundation

- Add `GROCERY_LIST` to `SubscriptionFeature` and database constraints.
- Configure FREE=false, PLUS=true, PRO=true in the plan feature matrix.
- Expose `groceryList` through `/api/v1/subscriptions/me/features`.
- Add Admin Feature Matrix support and entitlement snapshot compatibility.
- Add standard backend 403 enforcement to grocery endpoints.
- Add tests proving FREE denial, PLUS/PRO access and cache/client bypass resistance.

### Sprint 2 - Persisted grocery domain

- Add grocery list/session and grocery list item entities with Flyway migration.
- Store source type, source meal-plan id, source version and lifecycle status.
- Support generated, manual, excluded and quantity-overridden item states.
- Enforce user ownership, optimistic locking and sensible item/list limits.

### Sprint 3 - Grocery API

- Create/get active list from a meal plan.
- Check/uncheck, add manual item, update quantity and remove/exclude item.
- Return category, source, display quantity, normalized grams and purchased state.
- Keep API errors in the standard `ApiErrorResponseDto` contract.

### Sprint 4 - Refresh and merge

- Detect source meal-plan changes and expose an outdated state.
- Refresh generated items without silently overwriting manual items.
- Preserve purchased, excluded and quantity-overridden state where safe.
- Return a standard conflict response when an automatic merge is unsafe.

### Sprint 5 - Mobile integration handoff

- Keep the entry point inside Meal Plan Detail.
- Replace the read-only bottom sheet with a dedicated grocery screen.
- Add a searchable checklist, category grouping and progress summary.
- Show a paid feature gate for FREE users without calling protected data endpoints.
- Preserve the current approved UI until a separate UI implementation is approved.

### Sprint 6 - Quality and observability

- Service/controller/security and migration tests.
- Portion aggregation regression tests for recipe, serving, piece and mixed units.
- Concurrency tests for check/update and refresh conflicts.
- Product analytics events for opened, generated, refreshed and completed lists.
- Admin aggregate monitoring only; never expose a user's grocery contents in admin analytics.

## 7. Acceptance criteria

- FREE cannot access Grocery List through direct API calls.
- PLUS and PRO access is controlled through the admin-managed feature matrix.
- Existing subscription entitlement snapshots are respected.
- Generated quantities remain consistent with food logging portion conversion.
- Shopping progress persists independently from the meal plan.
- Meal-plan changes cannot silently destroy manual edits or purchased state.
- No frontend-only authorization exists.
- API, UI handoff and automated tests are complete.

## 8. Original hold decision

The current generated read-only list remains available in source but is not considered the final paid MVP. No migration, enum, endpoint contract or frontend design change is being made in this planning step.

Resume condition:

1. Product owner approves Grocery List as a PLUS and PRO feature.
2. Product owner approves a dedicated full-screen mobile workflow.
3. Sprint 1 starts with entitlement and backend security before UI expansion.