# PUT /itineraries/{itineraryId}/programs/excursions/{excursionId}/schedule

## CASMS-75 behavior change

CRM + CRUISE_UPDATE_DRAFT; validate input and If-Match; lock Cruise then DRAFT Itinerary, scope/lock occurrences or source and stop, enforce area/destination/window/capacity rules, atomically write and increment Cruise revision. Assignment is additive (201); schedule/removal returns revision (200). Invalid scope 404/422; stale/frozen 409; invalid input 400. Any failure rolls back the batch.

Reviewed delta only; full shared flow and important branches: [ITINERARY-PROGRAM-FLOWS.md](../ITINERARY-PROGRAM-FLOWS.md). Permission evidence: `hasRole('CRUISE_MANAGER') and hasAuthority('CRUISE_UPDATE_DRAFT')` (also see class annotations and central review).

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security
    participant Controller as ItineraryProgramController
    participant Service as Application service / policy
    participant Repository as JDBC / JPA repository
    participant DB as PostgreSQL
    Client->>Security: PUT /itineraries/{itineraryId}/programs/excursions/{excursionId}/schedule
    Security->>Controller: Existing authentication and effective authorization
    Controller->>Service: excursion request
    Service->>Service: Validate staged mutation; lock Cruise and Itinerary; validate revision/scope
    Service->>Repository: SELECT FOR UPDATE / FOR SHARE; bound INSERT or UPDATE; revision increment
    Repository->>DB: Read or mutate in declared transaction
    alt Validation, authorization, revision or integrity failure
        Service-->>Client: Existing 400 / 401 / 403 / 404 / 409 / 422; mutation rolls back
    else Valid operation
        DB-->>Service: Current result / committed mutation
        Service-->>Controller: Existing DTO or documented new program response
        Controller-->>Client: HTTP success
    end
```

## Staff scheduling constraint

A schedule edit checks active staff duties in the database transaction. An overlapping shift is rejected with 409 STAFF_WORK_TIME_CONFLICT. See [work scheduling](../ITINERARY-PROGRAM-FLOWS.md#dịch-vụ-và-ca-hoạt-động).
