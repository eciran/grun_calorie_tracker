# Frontend Handoff: Grocery List MVP

**Date:** 2026-07-29
**Backend status:** Sprints 1-4 complete
**Frontend scope:** Integration contract only. Do not change the approved mobile visual design without product approval.

## 1. Product placement and entitlement

- Entry point: **Meal Plan Detail**.
- Tapping `Grocery list` opens a dedicated checklist screen, not a read-only bottom sheet.
- Read `GET /api/v1/subscriptions/me/features` before showing the entry point.
- `groceryList=true`: enable entry and API calls.
- `groceryList=false`: show the existing paid-feature gate. Do not call `/api/v1/grocery-lists/**` behind the gate.
- The backend remains authoritative and returns `403` if a client bypasses the gate.
- Current matrix intent: PLUS and PRO can be enabled; FREE is disabled. Always trust the resolved feature response instead of hardcoding plan names.

Suggested query keys:

```ts
['subscription-features']
['grocery-list', listId]
['grocery-list', 'meal-plan', mealPlanId]
```

Invalidate `['subscription-features']` immediately after login, restore purchase, subscription refresh, logout, account switch, or foreground entitlement refresh.

## 2. API flow

All endpoints require `Authorization: Bearer <accessToken>`.

| Action | Method and path | Request |
|---|---|---|
| Create or reopen active list | `POST /api/v1/grocery-lists/meal-plans/{mealPlanId}` | none |
| Load persisted list | `GET /api/v1/grocery-lists/{listId}` | none |
| Add manual item | `POST /api/v1/grocery-lists/{listId}/items` | manual item body |
| Check/uncheck item | `PATCH /api/v1/grocery-lists/{listId}/items/{itemId}/purchased` | `purchased`, item `expectedVersion` |
| Change item quantity | `PUT /api/v1/grocery-lists/{listId}/items/{itemId}/quantity` | quantity fields, item `expectedVersion` |
| Remove item | `DELETE /api/v1/grocery-lists/{listId}/items/{itemId}?expectedVersion={itemVersion}` | none |
| Merge changed meal plan | `POST /api/v1/grocery-lists/{listId}/refresh` | list `expectedVersion` |
| Complete list | `POST /api/v1/grocery-lists/{listId}/complete` | none |
| Archive list | `DELETE /api/v1/grocery-lists/{listId}` | none; returns `204` |

`POST .../meal-plans/{mealPlanId}` is idempotent for the active list. It returns the existing active list instead of creating duplicates.

## 3. Response model

```ts
export type GroceryList = {
  id: number;
  sourceMealPlanId: number;
  sourceMealPlanName: string;
  sourceUpdatedAt: string;
  currentSourceUpdatedAt: string;
  sourceOutdated: boolean;
  status: 'ACTIVE' | 'COMPLETED' | 'ARCHIVED';
  totalItems: number;
  visibleItems: number;
  purchasedItems: number;
  version: number;
  createdAt: string;
  updatedAt: string;
  items: GroceryListItem[];
};

export type GroceryListItem = {
  id: number;
  foodItemId: number | null;
  displayName: string;
  category: GroceryCategory;
  source: 'GENERATED' | 'MANUAL';
  displayQuantity: number;
  displayUnit: FoodPortionUnit;
  normalizedGrams: number | null;
  plannedUses: number;
  purchased: boolean;
  excluded: boolean;
  sourceRemoved: boolean;
  quantityOverridden: boolean;
  version: number;
};
```

`GroceryCategory`:

```ts
'PRODUCE' | 'MEAT_AND_SEAFOOD' | 'DAIRY_AND_EGGS' | 'BAKERY' |
'GRAINS_AND_PASTA' | 'PANTRY' | 'FROZEN' | 'BEVERAGES' | 'OTHER'
```

`FoodPortionUnit`:

```ts
'GRAM' | 'MILLILITER' | 'TABLESPOON' | 'TEASPOON' |
'SLICE' | 'SERVING' | 'PIECE'
```

## 4. Visibility and grouping rules

- Main checklist: show only `excluded=false && sourceRemoved=false`.
- Progress: use backend `purchasedItems / visibleItems`; do not calculate from `totalItems`.
- Search locally across the already loaded `displayName`; no API call per keystroke.
- Group visible rows by `category` in the backend-provided order or a fixed localized category order.
- `source=MANUAL`: user-created row; deleting it permanently removes it.
- `source=GENERATED`: meal-plan row; deleting it sets `excluded=true` so later refresh does not silently restore the user choice.
- `sourceRemoved=true`: the ingredient no longer exists in the current meal plan. Hide it from the default checklist; it is retained as merge history.
- `quantityOverridden=true`: the user changed quantity; refresh preserves that quantity.

## 5. Refresh UX

When `sourceOutdated=true`, show one non-blocking banner:

- Message: the meal plan changed and the grocery list can be updated.
- Primary action: refresh.
- Do not refresh automatically; that could surprise a user while shopping.

Request:

```json
{ "expectedVersion": 3 }
```

Refresh guarantees:

- manual items remain;
- purchased state remains;
- excluded generated items remain excluded;
- user quantity overrides remain;
- new meal-plan ingredients are added;
- removed source ingredients are marked `sourceRemoved` rather than deleted.

On success, replace the entire grocery-list query value with the response. Do not merge response arrays on the client.

## 6. Mutation and conflict handling

Every successful mutation returns the full current list except archive. Replace the cached list atomically with that response.

For item mutations, send the item's current `version`. For refresh, send the list's current `version`.

On `409`:

1. Do not retry the mutation automatically.
2. Refetch `GET /api/v1/grocery-lists/{listId}`.
3. Show a short message that the list changed and has been reloaded.
4. Require the user to repeat the intended action against the new version.

Optimistic UI is acceptable for purchased toggles, but keep the previous list snapshot and roll back on any error. Quantity, remove, refresh, complete, and archive should use a visible pending state and prevent duplicate taps.

## 7. Request examples

Add manual item:

```json
{
  "displayName": "Coffee filters",
  "category": "PANTRY",
  "displayQuantity": 1,
  "displayUnit": "PIECE",
  "normalizedGrams": null
}
```

Check an item:

```json
{
  "purchased": true,
  "expectedVersion": 2
}
```

Override quantity:

```json
{
  "displayQuantity": 500,
  "displayUnit": "GRAM",
  "normalizedGrams": 500,
  "category": "PRODUCE",
  "expectedVersion": 2
}
```

## 8. Error contract

Errors use the standard body:

```ts
type ApiErrorResponse = {
  timestamp: string;
  status: number;
  error: string;
  code?: string;
  fieldErrors?: Array<{ field: string; code: string }>;
  message: string;
  path: string;
  correlationId: string;
};
```

Handling:

- `400`: request validation or list limit; keep form data and show field-safe feedback.
- `401`: use the shared auth refresh/login flow.
- `403`: invalidate subscription features and show the paid-feature gate.
- `404`: list or meal plan is unavailable/does not belong to the user; return to Meal Plan Detail.
- `409`: stale version or unsafe merge; refetch as described above.

Log `correlationId` in client diagnostics. Do not show raw backend exception text.

## 9. Screen states and accessibility

Required states:

- feature-gated;
- initial create/loading;
- active list with category groups;
- empty visible list;
- search with no result;
- refresh available;
- mutation pending;
- conflict reloaded;
- completed;
- recoverable error.

Checklist controls need accessible labels containing item name, quantity, unit and checked state. Search, add manual item, refresh and complete actions must remain keyboard/screen-reader reachable. Do not encode purchased or outdated state by color alone.

## 10. Acceptance checklist

- FREE never calls protected grocery endpoints.
- PLUS/PRO access follows `me/features.groceryList`, not a hardcoded plan check.
- Returning to the screen loads the persisted checklist.
- Purchased progress survives navigation and app restart.
- Search filters locally and category grouping remains stable.
- Manual items and quantity overrides survive meal-plan refresh.
- A stale mutation produces reload behavior, not an endless retry.
- `sourceRemoved` and `excluded` rows do not inflate visible progress.
- Existing approved mobile styling remains unchanged until separately approved.

## 11. Deferred from this handoff

- Sharing and household collaboration.
- Store aisle maps, pricing and retailer inventory.
- Pantry stock deduction.
- AI brand selection, substitutions and price optimization.
- Admin access to individual grocery contents.
