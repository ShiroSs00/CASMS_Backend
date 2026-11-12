# 01 — Identity & Access Management (IAM) Class Diagram

```mermaid
classDiagram
    direction TB
    title CASMS — Identity, Auth & Passenger Profile

    class User {
        +UUID userId
        +String username
        +String email
        +String passwordHash
        +String phoneNumber
        +UserStatus status
        +Instant createdAt
        +Instant updatedAt
        +hasRole(Role role) boolean
        +hasPermission(Permission perm) boolean
    }

    class Passenger {
        +UUID passengerId
        +String fullName
        +LocalDate dateOfBirth
        +String gender
        +String nationality
        +String documentType
        +String documentNumber
        +String phoneNumber
        +String email
        +Instant createdAt
    }

    class PassengerBleIdentity {
        +UUID bleIdentityId
        +String beaconMacAddress
        +String majorMinor
        +String encryptionKey
        +Boolean isActive
        +Instant pairedAt
    }

    class RolePermission {
        +UUID rolePermissionId
        +Role role
        +Permission permission
    }

    class UserStatus {
        <<enumeration>>
        ACTIVE
        SUSPENDED
        INACTIVE
        PENDING_ACTIVATION
    }

    class Role {
        <<enumeration>>
        ADMIN
        CRUISE_MANAGER
        SHIP_OPERATOR
        ACTIVITY_SERVICE_MANAGER
        CRUISE_STAFF
        POS_STAFF
        PASSENGER
    }

    class Permission {
        <<enumeration>>
        CRUISE_VIEW
        CRUISE_MANAGE
        BOOKING_VIEW
        BOOKING_MANAGE
        ACTIVITY_CREATE
        ACTIVITY_REGISTRATION
        PAYMENT_PROCESS
        FOLIO_CHARGE
        GANGWAY_SCAN
    }

    %% Relationships
    User "1" --> "0..1" Passenger : associatesProfile
    Passenger "1" *-- "0..1" PassengerBleIdentity : pairedWith
    User "0..*" --> "1..*" Role : hasRoles
    Role "1" o-- "1..*" RolePermission : definedBy
    RolePermission --> Permission : grants
    User --> UserStatus : hasStatus
```

