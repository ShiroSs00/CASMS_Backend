# GET /trip-planning/catalogs/sources/excursions

Requires `TRIP_READ`. The tour picker uses `destinationId` to restrict the list to planned templates for the chosen active destination. The optional `portId` limits results through `PortDestinationAccess`. Legacy templates missing a destination and scheduled occurrences are excluded.

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security
    participant Controller as TripCatalogController
    participant Repository as JPA repositories
    participant DB as PostgreSQL
    Client->>Security: GET excursion sources with destinationId
    Security->>Controller: Authenticate and check TRIP_READ
    Controller->>Repository: Query eligible planned templates and optional port access
    Repository->>DB: SELECT with bound filters and destination access
    DB-->>Repository: Templates and served destination IDs
    Repository-->>Controller: Filter by port coverage and keyword
    Controller-->>Client: 200
```
