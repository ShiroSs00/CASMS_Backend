# GET /booking/{bookingId}/contract/print

Coverage restored for a route inherited from main; **static baseline, semantic review pending**. Booking implementation is not changed by CASMS-75. Permission evidence: `hasAnyAuthority('BOOKING_READ'); service ownership checks require review`.

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security
    participant Controller as BookingController
    participant Service as BookingService
    participant Repository as Booking repositories
    participant DB as PostgreSQL
    Client->>Security: GET /booking/{bookingId}/contract/print
    Security->>Controller: Apply route security and handler annotation
    Controller->>Service: generateContractHtml(request context)
    Note over Service,DB: Lookup/ownership, transitive reads and error branches require semantic review
    Service->>Repository: Resolve booking contract input
    Repository->>DB: Read booking data
    Service-->>Controller: Generated HTML
    Controller-->>Client: 200 text/html;charset=UTF-8 or existing error mapping
```
