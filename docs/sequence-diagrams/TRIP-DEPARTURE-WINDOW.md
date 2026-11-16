# Departure window, duration and fleet eligibility

```mermaid
sequenceDiagram
    actor Planner
    participant Security as JWT and planner scope
    participant Controller as Trip or Cruise controller
    participant Service as TripService or CruiseService
    participant Projection as ScheduleMaterializationService
    participant Repo as Trip and Ship repositories
    participant DB as PostgreSQL
    Planner->>Security: Configure a template window or choose a voyage departure
    Security->>Controller: Authorized planner request
    Controller->>Service: Validate request and source revision
    Service->>Repo: Read template and approved source
    Repo->>DB: Load source data and saved window
    alt Incomplete window or departure outside bounds
        Service-->>Planner: Business validation error
    else Valid choice
        Service->>Projection: Resolve relative days and port-local times
        Projection-->>Service: Planned instants, elapsed time and local calendar counts
        alt Find suitable ships
            Service->>Repo: Filter operational, configured, available ships
        else Write assignment or change voyage times
            Service->>Repo: Lock ship and recheck eligibility
            Repo->>DB: Check competing voyage overlap and capacity
            Service->>DB: Save valid voyage and dependent changes
        end
        Service-->>Controller: Template, preview, candidates or voyage response
    end
```

Template dates remain relative. An optional pair of inclusive earliest/latest planned departure instants supports existing templates. Window values are included in source fingerprinting and checked on preview and planned updates; arrival and departure instants keep their port timezone. Voyage elapsed seconds are separate from local days and midnights in the departure-port timezone.

New voyages require a positive passenger count. Both passenger field names are accepted. A voyage can be created before ship assignment. Search and assignment exclude unconfigured ships; mutating paths lock the ship and recheck availability, including date changes with the same ship.
