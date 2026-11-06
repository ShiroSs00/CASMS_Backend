# Reviewed service and activity flows

Manually compared with controllers, service/repository implementations, mutation guard, V90, SecurityConfig and GlobalExceptionHandler for this change. The diagrams below describe the changed branches; per-route source traces retain unrelated behavior. Paths are relative to `/api`.

## Service catalog

```mermaid
sequenceDiagram
    actor Client
    participant Security as JWT and method security
    participant Controller as ServicepackageController
    participant Service as ServicepackageService
    participant Repository as ServiceRepository / CategoryRepository
    participant DB as PostgreSQL
    Client->>Security: GET services or POST/PUT service metadata
    alt Missing authentication or required authority
        Security-->>Client: 401 / 403
    else Authorized
        Security->>Controller: Read or validate catalog DTO
        alt Invalid bean constraints
            Controller-->>Client: 400
        else Valid input
            Controller->>Repository: Resolve service/category on update
            Repository->>DB: SELECT
            Controller->>Service: saveService in transaction
            Service->>Service: Validate stage, policy, limit, zone, USD, price, capacity
            alt Invalid policy or duplicate code
                Service-->>Client: 400 / 409; transaction rolls back
            else Valid
                Service->>Repository: save catalog without occurrence time/staff
                Repository->>DB: INSERT/UPDATE Service
                Service-->>Controller: Service
                Controller-->>Client: 201 create / 200 read or update, extended ServiceView
            end
        end
    end
```

Catalog creation requires ACTIVITY_SERVICE_MANAGER + SERVICE_CREATE, update SERVICE_UPDATE and reads SERVICE_READ. Missing referenced category is 404. Database invariant errors map through the global handler. New fields are also returned by existing service lifecycle responses; changing status keeps existing lifecycle validation.

## Prepaid reservation and completion

```mermaid
sequenceDiagram
    actor Client as Passenger / authorized executor
    participant Security as JWT and method security
    participant Controller as ServicepackageController / PosController
    participant Service as ServiceRequestService
    participant Grant as CruisePackageEntitlementService
    participant Quota as ServiceUsageQuotaService
    participant Repository as Request / Service / Schedule / Usage repositories
    participant DB as PostgreSQL
    Client->>Security: Create/edit request or execute action
    Security->>Controller: Authority and POS resource scope where applicable
    Controller->>Service: Invoke transactional operation
    alt Create or edit reservation
        Service->>Repository: Lock Service and optional ServiceSchedule; resolve confirmed passenger booking
        Repository->>DB: SELECT FOR UPDATE
        Service->>Service: Check owner, ship availability, cruise, purchase stage and schedule scope/capacity
        opt Fare-backed prepaid request
            Service->>Grant: reserve / validateServiceReservation
            Grant->>DB: Share-lock booking; lock purchased grant; count used and pending quota
            Grant-->>Service: Purchased grant ID or entitlement error
        end
        Service->>Service: Snapshot usage mode, policy, limit and time zone on create
        Service->>Quota: Check used plus pending, excluding this request on edit
        Quota->>DB: SUM ServiceUsage and active ServiceRequest, filtered by passenger/service/period
        alt Quota exhausted, wrong scope or no purchased paid entitlement
            Service-->>Client: Business error; rollback
        else Eligible
            Service->>Repository: Save request
            Repository->>DB: INSERT/UPDATE ServiceRequest
            Controller-->>Client: 201 create / 200 edit with snapshot and fulfilledQuantity
        end
    else Complete request
        Service->>Repository: Lock request, then Service
        Repository->>DB: SELECT FOR UPDATE
        alt Already completed by the same assigned executor
            Service-->>Controller: Existing result, no additional usage or fee
        else In progress and assigned executor matches
            Service->>Service: Confirm active booking/cruise and actual cruise/schedule dates
            Service->>Quota: Recheck quota for actual local usage day
            opt Purchased grant attached
                Service->>Grant: fulfill; verify remaining total, unit and usage window
                Grant->>DB: Lock grant and read ServiceUsage total
            end
            Service->>Repository: Save one ServiceUsage with SERVICE_COMPLETE request identity
            Service->>Repository: Set request COMPLETED and completedAt
            Repository->>DB: Atomic usage INSERT and request UPDATE
            Controller-->>Client: 200 with fulfilledQuantity
        else Invalid state or actor
            Service-->>Client: Existing transition error; no usage
        end
    else Cancel or reject
        Service->>Repository: Lock request and change eligible status
        Service->>DB: Reverse linked POS ledger if present
        Note over Service,Quota: Cancelled/rejected request no longer reserves quota
        Controller-->>Client: 200
    end
```

Ownership violations are 403; missing resources are 404; invalid DTOs/arguments are 400; BusinessException quota, entitlement and purchase-stage errors are 409 by default. Schedule/cruise mismatch explicitly returns 422. Existing invalid lifecycle state maps to 409 (schedule-conflict branches map to 422). Quota uses `usageTimeZoneSnapshot` calendar boundaries, counts each booking passenger separately and excludes ONBOARD/LEGACY requests. PER_DAY requests require a schedule. A fare total quota and service period quota are cumulative constraints. Cancellation does not change the booking. Read routes return the same extended ServiceRequestView through both normal and POS mappers. Existing request list/detail ownership and POS scope checks remain mandatory.

## Onboard retail

```mermaid
sequenceDiagram
    actor Staff
    participant Security as JWT / POS authorization
    participant Controller as PosController
    participant Service as PosService
    participant Requests as ServiceRequestService
    participant Repository as POS / account / service / request / charge repositories
    participant DB as PostgreSQL
    Staff->>Security: POST pos/sales, quantity 1, idempotency key
    Security->>Controller: Validate actor and DTO
    Controller->>Service: sale in transaction
    Service->>Repository: Resolve session, terminal, area, resource permissions and active cruise passenger
    Service->>Repository: Lock onboard account and Service
    Repository->>DB: Scope reads and row locks
    alt Existing idempotent sale
        Service-->>Controller: Existing sale
    else New operation
        Service->>Service: Check onboard/independent flags, positive USD price and spending cap
        Service->>Requests: createForPos, exactly one use
        Requests->>DB: Validate schedule capacity and persist ONBOARD request
        Service->>Repository: Save sale, account transaction and Charge
        Repository->>DB: Atomic financial writes
        Controller-->>Staff: Sale response
    end
    Note over Requests,DB: Later complete writes ServiceUsage only; cancel/reject reverses original charge
```

Missing JWT/permissions or out-of-scope terminal, area, service, staff or passenger reject before sale. `quantity != 1` is a 400 validation error. A new key creates a new charged operation; retrying the same key preserves existing idempotency payload checks. No prepaid quota is applied to ONBOARD requests. Insufficient allowance and existing POS business errors retain their mappings. No second fee is charged on completion.

## Activity catalog, itinerary and occurrence

```mermaid
sequenceDiagram
    actor Manager
    participant Security as JWT and method security
    participant Controller as ActivityController / CruiseScopedProgramsController
    participant Service as ActivityService / trip and operational mapping services
    participant Guard as CruisePlanMutationGuard / CruiseMainflowService
    participant Repository as Activity / Cruise / TripPlanEntry repositories
    participant DB as PostgreSQL
    Manager->>Security: POST activities or PUT activities/catalog/id
    Security->>Controller: ACTIVITY_CREATE / ACTIVITY_UPDATE
    Controller->>Controller: Validate metadata and reject schedule/staff fields
    Controller->>Service: saveCatalog in transaction
    Service->>Repository: Save independent PLANNED catalog row
    Repository->>DB: catalog_entry=true, no cruise/time/venue/staff
    Controller-->>Manager: 201 create / 200 update
    Manager->>Security: POST cruise-plans/cruiseId/activities/from-catalog + If-Match
    Security->>Controller: CRUISE_MANAGER and CRUISE_UPDATE_DRAFT
    Controller->>Guard: Lock and validate editable cruise and revision
    Guard->>DB: Lock Cruise and plan; reject locked/fixed structural edits
    Controller->>Service: scheduleCatalog
    Service->>Repository: Lock independent source, copy metadata into new occurrence
    Service->>Repository: Resolve cruise and venue; check time bounds and conflicts
    alt Invalid source, venue, time or state
        Service-->>Manager: 400 / 404 / 409 / 422; rollback
    else Valid occurrence
        Service->>Repository: Save timed occurrence with sourceActivityId
        Repository->>DB: INSERT OnboardActivity with catalog_entry=false
        Controller->>Guard: Finish existing cruise revision workflow
        Controller-->>Manager: 200 ActivityView
    end
    opt Use trip schedule version instead of direct flexible-cruise scheduling
        Manager->>Service: Existing draft entry with sourceActivityId and planned relative day/time
        Service->>DB: Save and validate TripPlanEntry through existing workflow
        Service->>DB: Materialize/sync occurrence with concrete time, venue and sourceActivityId
    end
```

Catalog edits never move or reprice existing occurrences. Existing timed rows are not rewritten by migration. `GET activities` excludes catalogs; `GET activities/catalog` lists them, including category and metadata; detail accepts either. Opening a catalog is rejected, and registration requires a matching cruise and OPEN timed occurrence. Catalog update uses its dedicated endpoint. Deletion checks registrations, package links, TripPlanEntry source references and occurrence source references. Catalog deletion has no cruise lock; occurrence mutations retain the existing cruise guard. Trip draft/source metadata resolution remains backward compatible with historical sources, while new catalog selections work through the same `sourceActivityId` field.

## Assign responsible user after scheduling

```mermaid
sequenceDiagram
    actor Manager
    participant Security as JWT and method security
    participant Controller as CruiseScopedProgramsController
    participant Service as CruiseMainflowService
    participant Repository as JdbcTemplate
    participant DB as PostgreSQL
    Manager->>Security: PUT cruise resource responsible-user + If-Match
    Security->>Controller: CRUISE_MANAGER and CRUISE_UPDATE_DRAFT
    Controller->>Service: start; lock editable plan and check revision
    Service->>DB: Cruise/plan row lock
    opt Non-null userId
        Controller->>Repository: Check ACTIVE CruiseStaff and ACTIVE User for same cruise
        Repository->>DB: SELECT EXISTS
        alt Ineligible user
            Controller-->>Manager: 422 RESPONSIBLE_STAFF_NOT_IN_CRUISE; rollback
        end
    end
    Controller->>Repository: UPDATE occurrence or service window WHERE id and cruise_id
    Repository->>DB: Set responsible_user_id or clear null
    alt Resource outside cruise or absent
        Controller-->>Manager: Existing scope rejection; rollback
    else Resource updated
        Controller->>Service: finish revision workflow
        Controller-->>Manager: 200 ApiResponse
    end
```

Activity and service-window read responses expose the field. This assignment is scheduling metadata, not permission delegation; execution still requires existing staff/resource permissions and the assigned ServiceRequest executor. All writes here share the class-level transaction; errors roll back resource changes and the revision together.
