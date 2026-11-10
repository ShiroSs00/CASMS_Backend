# GET /ships/{shipId}/areas/usages

## CASMS-75 behavior change

Read permits SHIP_CONFIGURATION_READ or CRM+CRUISE_UPDATE_DRAFT and returns FACILITY usage/model status/has_services.

Reviewed delta only; full shared flow and important branches: [ITINERARY-PROGRAM-FLOWS.md](../ITINERARY-PROGRAM-FLOWS.md). Permission evidence: `hasAuthority('SHIP_CONFIGURATION_READ') or (hasRole('CRUISE_MANAGER') and hasAuthority('CRUISE_UPDATE_DRAFT'))` (also see class annotations and central review).

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security
    participant Controller as ShipAreaUsageController
    participant Service as Application service / policy
    participant Repository as JDBC / JPA repository
    participant DB as PostgreSQL
    Client->>Security: GET /ships/{shipId}/areas/usages
    Security->>Controller: Existing authentication and effective authorization
    Controller->>Service: list request
    Service->>Service: Read physical area classifications
    Service->>Repository: SELECT FACILITY usage and enabled service evidence
    Repository->>DB: Read or mutate in declared transaction
    alt Validation, authorization, revision or integrity failure
        Service-->>Client: Existing 400 / 401 / 403 / 404 / 409 / 422; mutation rolls back
    else Valid operation
        DB-->>Service: Current result / committed mutation
        Service-->>Controller: Existing DTO or documented new program response
        Controller-->>Client: HTTP success
    end
```
