# Service usage policy and independent activity catalog

All paths below include the `/api` context path. Migration: `V90__service_usage_policy_and_activity_catalog.sql`.

## Service catalog (existing APIs extended)

`POST /api/servicepackage/services`, `PUT /api/servicepackage/services/{id}` accept the new optional fields below. Service list, detail, create, update and lifecycle responses return them. The existing service creation authority remains `ROLE_ACTIVITY_SERVICE_MANAGER` + `SERVICE_CREATE`; update requires `SERVICE_UPDATE`, read requires `SERVICE_READ`.

```json
{
  "serviceName": "Spa prepaid",
  "description": "Dịch vụ spa",
  "serviceType": "SPA",
  "price": 20,
  "currencyCode": "USD",
  "capacity": 10,
  "requiresSchedule": true,
  "canBuyPreCruise": true,
  "canBuyOnboard": true,
  "canSellIndependently": true,
  "unitCode": "SESSION",
  "preCruiseUsagePolicy": "PER_DAY",
  "preCruiseUsageLimit": 2,
  "usageTimeZone": "Asia/Ho_Chi_Minh"
}
```

- At least one purchase stage must be enabled. Both may be enabled.
- `UNLIMITED`: `preCruiseUsageLimit` must be null. Default for existing services.
- `PER_DAY`: positive limit per booking passenger, service and local calendar day in `usageTimeZone`. Requires scheduled reservations so the day is known before usage.
- `PER_CRUISE`: positive limit per booking passenger and service in the booked cruise.
- `usageTimeZone` must be a valid Java/IANA zone; default `Asia/Ho_Chi_Minh`.
- Existing request bodies remain accepted; omitted new settings retain current values on update. Sending a new policy also replaces its limit, including null for `UNLIMITED`.
- Service creation has no occurrence time, cruise or responsible user. Times are configured on `ServiceSchedule` through the existing serving-window APIs.

## Prepaid requests and onboard sales

Existing APIs: `POST /api/servicepackage/requests`, `PUT /api/servicepackage/requests/{id}`, `POST /api/servicepackage/requests/{id}/actions/{action}`, `POST /api/servicepackage/requests/{id}/cancel`, and their reads.

A request now needs a confirmed booking passenger (or an unambiguous confirmed booking for the authenticated passenger). The service must be available on the cruise ship. A schedule must belong to that service and cruise. Pre-departure registration checks `canBuyPreCruise`; requests for already purchased benefits may also be made during the voyage. Cancelled/completed voyages reject new requests.

For fare-based bookings, registration reserves a purchased service benefit. Legacy bookings also accept explicit `INCLUDED` service membership in their assigned package (including parent packages), with passenger scope, matching unit and total quantity checks. A priced service without such a purchased service benefit returns `PREPAID_ENTITLEMENT_REQUIRED`; the onboard retail path is POS. This prevents a normal request from bypassing the financial sale. Free services remain usable subject to the same booking, ship and quota validation. No new standalone pre-cruise payment API is introduced.

Requests snapshot `usageMode`, `usagePolicySnapshot`, `usageLimitSnapshot` and `usageTimeZoneSnapshot`. Reads also expose `fareBenefitSnapshotId` and `fulfilledQuantity`. Existing requests retain `LEGACY` mode to avoid inventing historical purchase terms.

Pending/accepted/in-progress requests reserve quota; cancellation/rejection releases it. Fare benefit total quotas also include pending service reservations. Completion checks the assigned executor, confirmed active cruise and actual schedule/cruise dates, then atomically writes `ServiceUsage` and completes the request. Repeating completion by the same executor is idempotent. Daily and total fare quotas both apply; daily reset never replenishes a purchased total fare quota.

`POST /api/pos/sales` now requires `quantity: 1` for a service sale. Every new sale is a separately charged operation; the same idempotency key replays the original sale. The service must permit independent onboard sale and the cruise must be in progress. Existing POS account/area/device/resource/staff authorization, spending caps and charge reversal rules remain in force. The charge is created at the sale; completion records usage without charging again. Cancel/reject reverses the existing linked charge. These rules also apply to `/api/pos/requests/{requestId}/actions/{action}`.

## Independent activity catalog

**Changed:** `POST /api/activities` now creates an independent catalog entry, not a timed cruise occurrence. Authority: `ACTIVITY_CREATE`; response: 201 with `ActivityView`.

```json
{
  "activityName": "Yoga",
  "description": "Yoga buổi sáng",
  "category": "SPORTS_WELLNESS",
  "capacity": 20,
  "price": 0,
  "currencyCode": "USD"
}
```

`startTime`, `endTime`, `cruiseId`, `deckId`, `cabinId`, `location` and `responsibleUserId` must be absent/null here; sending a value returns bean validation error (400). Existing scheduled activity rows stay intact.

**New:**

| Method | Path | Purpose / access |
|---|---|---|
| GET | `/api/activities/catalog` | Independent activity options; `ACTIVITY_READ`, or cruise manager with `CRUISE_UPDATE_DRAFT` |
| PUT | `/api/activities/catalog/{id}` | Edit catalog metadata; same body as create; `ACTIVITY_UPDATE` |
| POST | `/api/cruise-plans/{cruiseId}/activities/from-catalog` | Create a cruise occurrence from catalog; cruise manager + `CRUISE_UPDATE_DRAFT`, `If-Match` revision |
| PUT | `/api/cruise-plans/{cruiseId}/activities/{id}/responsible-user` | Assign/clear occurrence coordinator; same cruise edit access and revision |
| PUT | `/api/cruise-plans/{cruiseId}/serving-windows/{id}/responsible-user` | Assign/clear service-window coordinator; same cruise edit access and revision |

Schedule request (200 with the created occurrence):

```json
{
  "sourceActivityId": "<catalog activity UUID>",
  "startTime": "2026-12-01T01:00:00Z",
  "endTime": "2026-12-01T02:00:00Z",
  "location": "Deck 2",
  "capacity": 15
}
```

Optional `deckId` and `cabinId` must belong to the cruise ship; `capacity` defaults to the catalog value. Name/category/description/price/currency are copied into the occurrence. The catalog stays unchanged. Times must fit the cruise; overlapping live occurrences at the same location are rejected. These mutations follow the existing editable-cruise and fixed-itinerary rules; revision conflicts and locked plans are rejected. Use the existing trip-version `sourceActivityId` flow for a fixed itinerary; materialization now keeps `sourceActivityId` on the operational activity.

Responsible-user body: `{"userId":"<active cruise staff UUID>"}`; use `{"userId":null}` to clear. An inactive user or a user not actively assigned to that cruise returns `RESPONSIBLE_STAFF_NOT_IN_CRUISE` (422). Cross-cruise resources are rejected. This coordinator field does not grant permissions or replace per-request executors and existing operational staff assignments.

`ActivityView` now returns `catalogEntry`, `sourceActivityId`, `category`, `responsibleUserId`. `GET /api/activities` returns occurrences; use `/catalog` for independent definitions. `GET /api/activities/{id}` can read either. Use existing `PUT /api/activities/{id}` or cruise-scoped update to edit an occurrence. Catalog entries cannot be opened, started or registered against. Referenced catalog entries cannot be deleted. `ServiceScheduleView` and the cruise serving-window list return `responsibleUserId`.

## Client migration

1. Load `/activities/catalog` for the activity picker and create catalog entries through `/activities` without schedule fields.
2. Attach the selected activity through a trip-version entry (`sourceActivityId`) or the new cruise scheduling API. Configure date/time/venue on this schedule, then staff on the occurrence.
3. Configure service stages and prepaid quota on the service catalog. Create service windows separately using existing `/api/cruise-plans/{cruiseId}/serving-windows` or `/api/servicepackage/schedules`.
4. Use requests for purchased service benefits; use one POS sale per onboard retail use. Show the policy snapshot/fulfilled quantity from request reads.

Sequence review and error branches: [reviewed flows](sequence-diagrams/SERVICE-ACTIVITY-FLOWS.md).
