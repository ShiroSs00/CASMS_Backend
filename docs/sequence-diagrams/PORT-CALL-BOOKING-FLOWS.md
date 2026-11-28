# Port calls, activity scheduling and onboard bookings

The existing activity, itinerary-program, service request and POS controllers keep their authorization and record scope. Activity catalog definitions have no occurrence time. New occurrences and rescheduling use the cruise end or applicable port call cutoff. Shore programs keep the existing exact port, return, tender and disembarkation checks.

```mermaid
sequenceDiagram
    actor Client
    participant Security as JWT and route authorization
    participant Controller as Service request, POS or activity controller
    participant Service as Booking or registration service
    participant Policy as PortCallBookingPolicy
    participant Repository as CruiseStopRepository
    participant DB as PostgreSQL
    Client->>Security: Submit new onboard reservation
    Security->>Controller: Authenticated request
    Controller->>Service: Validate booking ownership and service scope
    opt Existing service operation key
        Service-->>Controller: Replay saved result
    end
    Service->>Policy: Check gate at current time
    Policy->>Repository: Load active voyage stops under shared read locks
    Repository->>DB: Query stops in itinerary order
    alt Arrived or overdue and actual departure missing at a mid-voyage call
        Policy-->>Controller: 409 ONBOARD_BOOKING_CLOSED
    else Final disembarkation or voyage ended
        Policy-->>Controller: 409 or existing terminal status response
    else Gate open
        Service->>Service: Validate usage interval, schedule and capacity
        alt Usage interval outside configured hours
            Service-->>Controller: 422 SERVICE_OUTSIDE_OPERATING_HOURS
        else Reservation allowed
            Service->>DB: Write new reservation and quota changes
            Service-->>Controller: Saved reservation
        end
    end
```

At a middle call, planned arrival triggers ARRIVAL_PENDING when actual arrival is missing. Actual arrival puts the call IN_PORT. Planned departure with missing actual departure keeps the gate closed. Actual departure opens it again. Final disembarkation closes new requests when its scheduled interval ends, with cruise end as the overall cutoff. Existing bookings and previous idempotent results are preserved.

Actual arrival and departure writes lock the stop and reject future or reversed timestamps. Stop responses include the computed port call state. A scheduled job inserts a deduplicated in-app alert for active Operations Managers after an unresolved planned middle-call timestamp, with at most one notification per stop, phase and recipient. The polling interval is configurable with `casms.port-call-alert.poll-millis`.

Service catalog mutations optionally set `operatingPeriods`. Deployment configuration controls the three local-time boundaries with `casms.services.hours.daytime`, `evening`, and `nighttime`. Restricted periods apply to the whole scheduled usage interval, or the current instant for immediate requests. An unscheduled pre-cruise request for a restricted service needs a schedule. Existing services with empty periods retain unrestricted hours. Port schedules use the port timezone and onboard schedules use the ship calendar zone.

An activity or tour with a newly set occurrence time must exceed 60 minutes, have at least two hours left before cutoff and finish before the combined 30-minute checkout and 30-minute preparation intervals. Existing authorized ports and itinerary edit paths retain their own resource locking and overlap checks.

## Final-port shore excursion timing

The minimum window runs from planned arrival to the valid start of passenger disembarkation. A final-port window of 120 minutes passes this check. Existing excursion duration, return, port-call and checkout checks still apply. A port call without a valid disembarkation window follows its existing validation.

```mermaid
sequenceDiagram
    actor User
    participant Security as JWT and route permissions
    participant Controller as Excursion or itinerary program controller
    participant Service as ShoreExcursionService or ItineraryProgramService
    participant Data as Repositories or JdbcTemplate
    participant DB as PostgreSQL
    User->>Security: Create excursion or change schedule
    Security->>Controller: Authorize request
    Controller->>Service: Validate and write in a transaction
    Service->>Data: Load excursion and port call for scheduling
    Data->>DB: Read planned arrival and disembarkation plan
    DB-->>Data: Stored port-call times
    Data-->>Service: Schedule inputs
    Service->>Service: Run existing port-call checks and verify the current disembarkation plan
    alt New schedule with valid disembarkation less than 120 minutes after arrival
        Service-->>Controller: Roll back and return SHORE_EXCURSION_FINAL_PORT_WINDOW_TOO_SHORT
        Controller-->>User: HTTP 422
    else Window passes or no valid disembarkation applies
        Service->>Data: Save once remaining validations pass
        Data->>DB: Persist excursion or schedule
        Service-->>Controller: Return saved result
        Controller-->>User: Success response
    end
```

## Exact daily service hours

The service catalogue accepts `operatingWindows` on creation and update. Each entry has `startsAt` and `endsAt` as a local time, such as `06:30` and `09:00`. A closing time earlier than the opening time ends on the next day. Equal endpoints are invalid. Multiple windows can cover one usage continuously when they touch or overlap. The end boundary is exclusive for a new usage start.

Omitting the field preserves the stored setting. Exact windows take precedence over legacy `operatingPeriods`. An explicit empty list removes the time restriction. Catalog list, detail and status responses include the field, and public cruise experiences expose it for ship-assigned services. The rule uses the existing port or ship timeline timezone. Creating a cruise does not require adding a service window. An advance request for a time-restricted service still needs a selected schedule to identify the intended time of use.

```mermaid
sequenceDiagram
    actor Manager
    actor User as Customer or authorized POS operator
    participant Security as JWT, permissions and POS scope
    participant Catalog as ServicepackageController
    participant Requests as ServiceRequestService
    participant Hours as ServiceOperatingHours
    participant Repo as Service and request repositories
    participant DB as PostgreSQL
    Manager->>Security: POST or PUT catalogue service with operatingWindows
    Security->>Catalog: Authorize catalogue mutation
    Catalog->>Catalog: Validate local time pairs
    Catalog->>Repo: Save service catalogue hours
    Repo->>DB: Persist operating_windows JSONB
    Catalog-->>Manager: Saved service view
    User->>Security: Create a request or post a POS sale
    Security->>Requests: Authorized request or POS service usage
    Requests->>Repo: Resolve service and usage schedule in write transaction
    Repo->>DB: Load catalogue and schedule
    Requests->>Hours: Validate usage against local catalogue hours
    alt Usage crosses closed time
        Hours-->>User: HTTP 422 SERVICE_OUTSIDE_OPERATING_HOURS
    else Usage fits
        Requests->>Repo: Continue existing booking and capacity validation
        Repo->>DB: Save request and related writes
        Requests-->>User: Success response
    end
```
