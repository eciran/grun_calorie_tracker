# Backend Handoff - Advanced Fasting Implemented

Date: 2026-07-28
Status: Backend implementation complete
Consumers: GRUN mobile application

## 1. Access and authorization

- All advanced routes are under `/api/v1/fasting/advanced`.
- Routes require `FASTING_ADVANCED`.
- Advanced analytics additionally follows the existing advanced analytics entitlement rules.
- The backend is the authorization boundary. UI visibility must not replace backend checks.
- All resources are owner-scoped. The client must not cache one user's responses under another user's session.

## 2. Program flow

Available endpoints:

- `POST /api/v1/fasting/advanced/eligibility`
- `POST /api/v1/fasting/advanced/programs`
- `GET /api/v1/fasting/advanced/programs`
- `GET /api/v1/fasting/advanced/programs/{id}`
- `PUT /api/v1/fasting/advanced/programs/{id}`
- `GET /api/v1/fasting/advanced/programs/{id}/preview`
- `POST /api/v1/fasting/advanced/programs/{id}/activate`
- `POST /api/v1/fasting/advanced/programs/{id}/pause`
- `POST /api/v1/fasting/advanced/programs/{id}/archive`

Program states are `DRAFT`, `ACTIVE`, `PAUSED` and `ARCHIVED`. Archived programs are terminal. Updating a program creates a new immutable version.

Draft creation supports `Idempotency-Key`. Mobile should create one key per user action and reuse it only when retrying the same request.

## 3. Occurrence and schedule flow

Available endpoints:

- `GET /api/v1/fasting/advanced/occurrences/{date}`
- `POST /api/v1/fasting/advanced/occurrences/{date}/start`
- `POST /api/v1/fasting/advanced/occurrences/{date}/skip`
- `POST /api/v1/fasting/advanced/occurrences/{date}/recalculate`
- `PUT /api/v1/fasting/advanced/occurrences/{date}/exception`
- `DELETE /api/v1/fasting/advanced/occurrences/{date}/exception`
- `GET /api/v1/fasting/advanced/occurrences/{date}/nutrition-summary`

Dates are user-profile-local dates in `YYYY-MM-DD` format. Do not convert a selected schedule date using the device timezone.

Supported schedule exceptions:

- `SKIP`
- `MOVE_START_TIME`
- `MOVE_REDUCED_DAY`

Exceptions apply only to future, mutable occurrences. A reduced-calorie day can move only inside the same ISO week. Concurrent edits for the same user are serialized by the backend.

## 4. Reduced-calorie day

`GET /occurrences/{date}/nutrition-summary` returns the canonical:

- planned calorie target;
- consumed calories;
- remaining calories;
- evaluated timestamp;
- adherence state.

Food and recipe log mutations recalculate a materialized reduced-calorie occurrence after transaction commit. Mobile must not produce its own canonical total.

## 5. Diary conflict flow

- `POST /api/v1/fasting/advanced/diary-conflicts/evaluate`
- `POST /api/v1/fasting/advanced/diary-conflicts/resolve`

The UI should evaluate before finalizing a food entry that may be inside a fasting window. Supported actions are returned by the backend. Resolution is atomic; do not end a fast and create the food log as two independent client requests.

Food diary responses may include server-calculated fasting-window context. This context is informational and does not alter calorie totals.

## 6. History correction

- `POST /api/v1/fasting/advanced/history`
- `PATCH /api/v1/fasting/advanced/history/{sessionId}`
- `DELETE /api/v1/fasting/advanced/history/{sessionId}`

Delete is an archive operation. Active sessions cannot be edited through historical correction. UI should refresh history, occurrence detail and analytics after a successful mutation.

## 7. Reminder settings

- `GET /api/v1/fasting/advanced/reminder-settings`
- `PUT /api/v1/fasting/advanced/reminder-settings`

Fields:

```json
{
  "enabled": true,
  "preStartEnabled": true,
  "startEnabled": true,
  "nearingCompletionEnabled": true,
  "completionEnabled": true,
  "missedPlanEnabled": false,
  "preStartMinutes": 30,
  "nearingCompletionMinutes": 15
}
```

The account push setting, global fasting reminder setting, module setting and quiet hours are all enforced by backend. Disabling advanced reminders suppresses undelivered jobs.

Notification text follows `preferredLanguage`:

- `TR`: Turkish copy
- all other currently supported cases: English copy

Program pause/archive prevents future scheduled reminder generation.

## 8. Analytics

`GET /api/v1/fasting/advanced/analytics?start=YYYY-MM-DD&end=YYYY-MM-DD`

- Range is inclusive.
- Maximum range is 366 days.
- UI presets should use 7, 30 and 90 days.
- Missing values remain `null`, not zero.
- Use `minimumDataMet`, `minimumRecommendedOccurrences` and `dataQualityIndicators` before presenting conclusions.
- Analytics wording must describe association and adherence, not medical causation.

## 9. Errors

Use the response `code` for UI behavior and localization. Display `message` only as a fallback.

Expected shape:

```json
{
  "timestamp": "2026-07-28T19:00:00Z",
  "status": 409,
  "code": "INVALID_FASTING_PROGRAM_TRANSITION",
  "message": "Developer-readable fallback",
  "correlationId": "..."
}
```

For `401`, return to authentication recovery. For `403`, show the subscription/access state. For `409`, preserve form state and handle the stable code. Include `correlationId` in support reports.

## 10. Client cache invalidation

After program mutation, invalidate:

- program list and detail;
- preview;
- affected occurrences;
- fasting analytics.

After occurrence/history mutation, invalidate:

- occurrence detail;
- active fasting summary;
- history;
- fasting analytics.

After food/recipe mutation, invalidate:

- diary date;
- affected occurrence;
- reduced-day nutrition summary;
- fasting analytics.

After reminder update, invalidate only reminder settings.

## 11. Privacy behavior

Account GDPR export now includes:

- advanced fasting programs;
- reminder settings;
- schedule exceptions;
- history correction metadata;
- schedule exception audit metadata.

Free-text correction reasons and exception raw old/new snapshots are not exposed in the advanced fasting export section. Account deletion relies on verified PostgreSQL cascade rules for associated advanced fasting records.

No dedicated mobile UI change is required for this section unless the application provides a downloadable account export viewer.

## 12. Verification

Backend verification completed:

- Advanced Fasting test group: 51 tests passed.
- Dublin DST transition contract is covered.
- EN/TR reminder selection is covered.
- Schedule exception user locking is covered.
- V191, V193 and V194 cascade/constraint contracts are covered.

Implementation commits:

- `809f024 feat(fasting): include advanced data in GDPR export`
- `eb7c920 test(fasting): harden release behavior`

External release checks still required:

- real-device push notification delivery;
- staging PostgreSQL/Flyway smoke;
- production subscription entitlement smoke;
- final clinical/legal copy review.
