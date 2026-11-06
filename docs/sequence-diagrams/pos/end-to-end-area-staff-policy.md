# Luồng POS – khu vực – dịch vụ – nhân viên – permission

Source-reviewed 2026-10-30. Các route có prefix `/api`.

```mermaid
sequenceDiagram
    actor Manager
    actor Employee
    participant API as POS and Cruise controllers
    participant Service as PosService and PosAccessService
    participant Repo as JDBC and Spring Data repositories
    participant DB as PostgreSQL
    Manager->>API: ACTIVITY_SERVICE_MANAGER sets canonical service operation policy with SERVICE_UPDATE
    API->>DB: Save ServiceOperationPolicy with actor and time
    Manager->>API: Assign catalogue service to deck, then physical area
    API->>DB: Save deck assignment and area link inheriting source permissions by default
    Manager->>API: Assign employee to cruise through existing staff API
    API->>DB: Persist ACTIVE CruiseStaff and existing audit event
    Manager->>API: Assign cruise staff to physical area with optional access dates
    API->>Service: Validate POS_MANAGE and eligible employee/cruise/ship
    Service->>DB: Upsert AreaStaffAssignment; revoke old assignment sessions
    Manager->>API: Create POS and link permitted areas
    API->>DB: PosTerminal plus PosTerminalArea; resource operations inherit through area links within terminal caps
    opt POS manager restricts operations for this device and area
        Manager->>API: Configure optional restriction per terminal, area and service
        API->>DB: Save PosAreaServicePermission within source, area and terminal capabilities
    end
    Employee->>API: Open session with employee JWT, device secret, area and cruise
    API->>Service: Require POS_ACCESS and active employee
    Service->>Repo: Lock device/area; verify secret, enabled link and live area/CruiseStaff assignment
    Repo->>DB: Read current scope and access-window proof
    alt Missing or ambiguous assignment
        Service-->>Employee: 403 or 409; no session created
    else Exact assignment selected
        Service->>DB: Store token hash with employee, terminal, assignment and expiry
        Service-->>Employee: Session token and exact area/cruise context
        loop Each POS request
            Employee->>API: JWT and X-POS-Session
            API->>Service: Resolve proof and lock terminal then area
            Service->>DB: Re-check employee ownership, revocation, expiry, ACTIVE crew and current area link
            Service->>DB: Read live source, optional area restriction, terminal cap and optional POS override
            Service->>Service: Intersect all applicable sets; validate selected cruise data; disabled link or override denies
            alt Access/permission revoked or wrong cruise
                Service-->>Employee: 403; no usage written
            else Authorized internal usage
                Service->>DB: Lock account; enforce shared/member allowance; record usage and internal charge atomically
                Note over Service,DB: No Payment or actual money collection; failure rolls back every usage write
                Service-->>Employee: Recorded usage and available scoped operations
            end
        end
    end
```

Resource policy update 2026-10-30: canonical operations belong to Service or OnboardActivity and are managed only by ACTIVITY_SERVICE_MANAGER with the corresponding UPDATE authority. New area links inherit dynamically. Existing explicit area/device sets remain restrictions. Source -> optional area restriction -> terminal cap -> optional POS override determines each operation. Employee POS_ACCESS and active area/CruiseStaff proof remain mandatory. DELETE device policy keeps an explicit denial. V72 preserves old missing-policy denials and old financial/history records. Activities reuse registration, attendance and lifecycle state machines; no cash collection is introduced.
