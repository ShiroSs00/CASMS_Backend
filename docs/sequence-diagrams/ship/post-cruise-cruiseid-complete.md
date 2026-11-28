# POST /cruise/{cruiseId}/complete

```mermaid
sequenceDiagram
    actor Client
    participant Security as JWT and method security
    participant Controller as CruiseController
    participant Service as CruiseService
    participant Repository as Repositories
    participant DB as PostgreSQL
    Client->>Security: POST /cruise/{cruiseId}/complete
    alt Authentication or authorization rejected
        Security-->>Client: 401 or 403
    else Authorized request
        Security->>Controller: Require CRUISE_COMPLETE
        Controller->>Service: Complete transaction
        Service->>Repository: Lock cruise
        Repository->>DB: SELECT FOR UPDATE
        alt Lifecycle, return-time or closure check fails
            Service-->>Client: Reject and roll back
        else Closure allowed
            opt Assigned ship exists
        Service->>Repository: Lock ship
        Repository->>DB: SELECT ship FOR UPDATE
                alt Ship archived or nonoperational
                    Note over Service,Repository: Preserve ship status
        else Operational ship
            Service->>Repository: Check for another IN_PROGRESS cruise
            Repository->>DB: Read other voyages excluding current cruise
            alt Expiry already passed
                Service->>Repository: Reuse expiry notice claim and apply maintenance when no other voyage is underway
            else Another cruise is underway
                Service->>DB: Keep ship IN_SERVICE
            else Last ongoing cruise closes
                Service->>DB: Set ship READY
            end
                end
            end
            Service->>Repository: Save COMPLETED, release cabins, close itinerary and record audit
            Service-->>Controller: Cruise response after commit
            Controller-->>Client: 200 with COMPLETED
        end
    end
```

Ship and voyage changes commit together. An operational ship can return to READY on the expiry date or when a legacy profile has no known expiry. A known date before the current Vietnam date uses the [classification expiry flow](../SHIP-TYPE-FLOWS.md#automatic-maintenance-for-classification-expiry), with at most one manager notice per ship and expiry date.

## HR assignment lifecycle

When Cruise becomes COMPLETED, V100 closes ACTIVE staff rows in the same transaction and preserves history. Those employees become eligible for new assignments. See [assignment lifecycle](../ITINERARY-PROGRAM-FLOWS.md#một-chuyến-đang-hiệu-lực-cho-mỗi-nhân-viên).
