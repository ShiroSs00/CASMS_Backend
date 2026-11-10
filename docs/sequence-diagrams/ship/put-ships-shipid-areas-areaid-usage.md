# PUT /ships/{shipId}/areas/{areaId}/usage

## CASMS-75 behavior change

SHIP_MODEL_OBJECT_UPDATE (default Admin); lock exact ship FACILITY, reject active service before ACTIVITY_SPACE (422), update usage in transaction. V95 rejects incompatible reclassification (409 integrity). Missing scoped area 404.

Reviewed delta only; full shared flow and important branches: [ITINERARY-PROGRAM-FLOWS.md](../ITINERARY-PROGRAM-FLOWS.md). Permission evidence: `hasAuthority('SHIP_MODEL_OBJECT_UPDATE')` (also see class annotations and central review).

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security
    participant Controller as ShipAreaUsageController
    participant Service as Application service / policy
    participant Repository as JDBC / JPA repository
    participant DB as PostgreSQL
    Client->>Security: PUT /ships/{shipId}/areas/{areaId}/usage
    Security->>Controller: Existing authentication and effective authorization
    Controller->>Service: update request
    Service->>Service: Validate scope and classification; lock area
    Service->>Repository: UPDATE area_usage; V95 reclassification guard
    Repository->>DB: Read or mutate in declared transaction
    alt Validation, authorization, revision or integrity failure
        Service-->>Client: Existing 400 / 401 / 403 / 404 / 409 / 422; mutation rolls back
    else Valid operation
        DB-->>Service: Current result / committed mutation
        Service-->>Controller: Existing DTO or documented new program response
        Controller-->>Client: HTTP success
    end
```
