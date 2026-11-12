# Sơ Đồ Lớp Thiết Kế Chi Tiết: Phân Hệ Xác Thực & Phân Quyền (Identity & IAM Design Class Diagram)

Mô hình hóa chi tiết đầy đủ 4 tầng kiến trúc: **Controller ➔ Service ➔ Repository ➔ Entity** cho quản lý tài khoản người dùng, đăng nhập JWT, ma trận phân quyền RBAC và hồ sơ hành khách.

## Mã Mermaid UML (Tối ưu chuẩn Draw.io không bị đè dòng)

```mermaid
classDiagram
    direction TB

    %% 1. Controller Layer
    class AuthController {
        -AuthService authService
        +login(request) ApiResponse~LoginResponse~
        +refreshToken(refreshToken) ApiResponse~LoginResponse~
        +me(principal) ApiResponse~UserMeResponse~
        +logout() ApiResponse~Void~
    }

    class IdentityController {
        -IdentityService identityService
        +createPassenger(request) ApiResponse~PassengerResponse~
        +getPassengerDetail(id) ApiResponse~PassengerResponse~
        +updateRoles(userId, roleIds) ApiResponse
    }

    %% 2. Service Layer
    class AuthService {
        -UserRepository userRepo
        -JwtService jwtService
        -PasswordEncoder passwordEncoder
        +authenticate(username, password) LoginResponse
        +refreshAccessToken(refreshToken) LoginResponse
        +validateToken(token) boolean
    }

    class IdentityService {
        -UserRepository userRepo
        -PassengerRepository passengerRepo
        -RoleRepository roleRepo
        +findUserById(userId) Optional~User~
        +assignRolesToUser(userId, roleCodes) User
        +registerPassengerProfile(userId, dto) Passenger
    }

    %% 3. Repository Layer
    class UserRepository {
        +findByUsername(username) Optional~User~
        +findByEmail(email) Optional~User~
        +existsByUsername(username) boolean
        +save(user) User
    }

    class PassengerRepository {
        +findById(id) Optional~Passenger~
        +findByDocumentNumber(docNum) Optional~Passenger~
        +save(passenger) Passenger
    }

    class RoleRepository {
        +findByRoleName(roleName) Optional~Role~
        +save(role) Role
    }

    class PermissionRepository {
        +findByResourceName(res) List~Permission~
    }

    %% 4. Entity Layer
    class User {
        +UUID userId
        +String username
        +String email
        +String passwordHash
        +String phoneNumber
        +UserStatus status
        +Instant createdAt
    }

    class Passenger {
        +UUID passengerId
        +String fullName
        +LocalDate dateOfBirth
        +String nationality
        +String documentNumber
        +LocalDate documentExpiryDate
    }

    class Role {
        +UUID roleId
        +String roleName
        +String description
        +Boolean isSystemRole
    }

    class Permission {
        +UUID permissionId
        +String permissionCode
        +String resourceName
        +String action
    }

    %% Inter-Layer and Domain Relationships
    AuthController --> AuthService : delegatesTo
    IdentityController --> IdentityService : delegatesTo

    AuthService --> UserRepository : accesses
    IdentityService --> UserRepository : accesses
    IdentityService --> PassengerRepository : accesses
    IdentityService --> RoleRepository : accesses

    UserRepository ..> User : manages
    PassengerRepository ..> Passenger : manages
    RoleRepository ..> Role : manages
    PermissionRepository ..> Permission : manages

    User "1" <|-- "0..1" Passenger : profileOf
    User "0..*" o-- "1..*" Role : hasRoles
    Role "0..*" o-- "1..*" Permission : grants
```
