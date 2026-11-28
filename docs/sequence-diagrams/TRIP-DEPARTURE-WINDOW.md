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
    Service->>Service: Compare inclusive ship-local hour and minute
    alt Incomplete or reversed window, or departure minute outside bounds
        Service-->>Planner: Business validation error
    else Valid choice
        Service->>Projection: Resolve relative days and port-local times
        Projection-->>Service: Planned instants, elapsed time and local calendar counts
        alt Find suitable ships
            Note over Service: Read-only transaction
            Service->>Repo: Filter operational, configured, available ships
            Repo->>DB: Read candidate ships and operational profile JSONB
            Service->>Service: Keep ships with known classification expiry through Vietnam return date
            Service-->>Controller: Eligible candidates and projected schedule
        else Write assignment or change voyage times
            Note over Service: Write transaction
            Service->>Repo: Lock selected ship
            Repo->>DB: Read locked ship and operational profile JSONB
            alt Classification expiry missing or before Vietnam return date
                Service-->>Controller: 422, write transaction rolled back
            else Classification valid
                Service->>Repo: Check existing capacity, configuration and overlap conditions
                Repo->>DB: Read competing voyages, decks and cabins
                opt All eligibility checks pass
                    Service->>DB: Save valid voyage and dependent changes
                    Service-->>Controller: Voyage response
                end
            end
        end
    end
```

Template create, update and draft patch accept an optional pair of inclusive `HH:mm` bounds in the ship timezone (`Asia/Ho_Chi_Minh`). Legacy offset timestamps are converted to the ship clock. Dates, seconds and fractions are discarded for window comparison. Trip list, detail, clone and archive responses and eligible-source list and detail return the bounds as `HH:mm`. Omitting both bounds preserves an existing window on update; supplying only one or reversing the bounds returns `TRIP_DEPARTURE_WINDOW_INVALID`. Equal bounds allow a single minute.

PostgreSQL stores the bounds as minute-aligned local times. Migration V136 converts legacy timestamps in the ship timezone and orders any legacy pair whose clock endpoints reverse after conversion. Source fingerprints computed after the change include the normalized bounds.

Schedule preview, eligible ships, cruise creation and materialization, draft updates, change preview/apply and delay preview/apply enforce the same window. A departure outside the window produces `DEPARTURE_OUTSIDE_TRIP_WINDOW` in validation errors and delay blockers, and a window-specific message in impact preview. The actual departure and return, stop timings, ship availability and overlap checks continue to use full instants. Voyage elapsed seconds are separate from local days and midnights in the departure-port timezone.

New voyages require a positive passenger count. Both passenger field names are accepted. A voyage can be created before ship assignment. Search and assignment exclude unconfigured ships; mutating paths lock the ship and recheck availability, including date changes with the same ship.

The eligible-ship search and write guards compare the ship's known `classificationExpiryDate` with the effective voyage return date in `Asia/Ho_Chi_Minh`. The voyage may return on the expiry date. The search omits ships with missing or shorter validity. Create with a ship, draft ship or schedule edits, assignment, reassignment and apply-change return 422 for the same conditions. Error codes are `SHIP_CLASSIFICATION_EXPIRY_REQUIRED` and `SHIP_CLASSIFICATION_EXPIRES_BEFORE_CRUISE_END`. Source-linked creation uses the projected return and manual creation uses the validated input return. The `/cruises` alias uses the same handlers.
