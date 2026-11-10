# GET /itineraries/{itineraryId}/programs

## CASMS-75 behavior change

Read requires ITINERARY_READ or CRUISE_READ and returns occurrences, Cruise revision and per-record issues without writes.

Reviewed delta only; full shared flow and important branches: [ITINERARY-PROGRAM-FLOWS.md](../ITINERARY-PROGRAM-FLOWS.md). Permission evidence: `hasAnyAuthority('ITINERARY_READ','CRUISE_READ')` (also see class annotations and central review).

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security
    participant Controller as ItineraryProgramController
    participant Service as Application service / policy
    participant Repository as JDBC / JPA repository
    participant DB as PostgreSQL
    Client->>Security: GET /itineraries/{itineraryId}/programs
    Security->>Controller: Existing authentication and effective authorization
    Controller->>Service: read request
    Service->>Service: Read scoped occurrences and current policy issues
    Service->>Repository: SELECT programs, Cruise, stops, areas and planned destinations
    Repository->>DB: Read or mutate in declared transaction
    alt Validation, authorization, revision or integrity failure
        Service-->>Client: Existing 400 / 401 / 403 / 404 / 409 / 422; mutation rolls back
    else Valid operation
        DB-->>Service: Current result / committed mutation
        Service-->>Controller: Existing DTO or documented new program response
        Controller-->>Client: HTTP success
    end
```
