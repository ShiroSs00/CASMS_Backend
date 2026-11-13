# Sơ Đồ Lớp Thiết Kế Chi Tiết: Bảo Mật JWT & Xử Lý Ngoại Lệ Toàn Cục (Security & Global Exception Architecture)

Mô hình hóa chi tiết chuỗi lọc bảo mật Spring Security (`SecurityFilterChain`), xác thực Token JWT bất đối xứng, kiểm tra quyền hạn chi tiết (`CasmsPermissionEvaluator`), và bộ bắt ngoại lệ tập trung toàn hệ thống (`GlobalExceptionHandler`).

## Mã Mermaid UML (Tối ưu chuẩn Draw.io không bị đè dòng)

```mermaid
classDiagram
    direction TB

    %% 1. Security Configuration & Filter Chain
    class SecurityConfig {
        -JwtAuthenticationFilter jwtFilter
        -UserDetailsService userDetailsService
        +securityFilterChain(http) SecurityFilterChain
        +passwordEncoder() PasswordEncoder
        +authenticationManager(config) AuthenticationManager
    }

    class JwtAuthenticationFilter {
        -JwtService jwtService
        -UserDetailsService userDetailsService
        +doFilterInternal(request, response, filterChain) void
        -extractTokenFromHeader(request) String
    }

    class JwtService {
        -String secretKey
        -Long jwtExpirationMs
        +generateAccessToken(userDetails) String
        +generateRefreshToken(userDetails) String
        +extractUsername(token) String
        +isTokenValid(token, userDetails) boolean
    }

    class CasmsPermissionEvaluator {
        -RoleRepository roleRepo
        +hasPermission(authentication, targetDomain, permission) boolean
        +hasPrivilege(principal, requiredRole) boolean
    }

    class CasmsPrincipal {
        +UUID userId
        +String username
        +String email
        +Collection~GrantedAuthority~ authorities
        +getAuthorities() Collection
        +isAccountNonExpired() boolean
    }

    %% 2. Global Exception Handling Architecture
    class GlobalExceptionHandler {
        +handleBusinessException(ex) ResponseEntity~ApiResponse~
        +handleResourceNotFound(ex) ResponseEntity~ApiResponse~
        +handleValidationException(ex) ResponseEntity~ApiResponse~
        +handleUnauthorizedException(ex) ResponseEntity~ApiResponse~
        +handleGeneralException(ex) ResponseEntity~ApiResponse~
    }

    class BusinessException {
        +ErrorCode errorCode
        +String customMessage
        +getErrorCode() ErrorCode
    }

    class ResourceNotFoundException {
        +String resourceName
        +String fieldName
        +Object fieldValue
    }

    class ApiResponse {
        +int statusCode
        +String message
        +T data
        +Instant timestamp
        +success(data) ApiResponse
        +error(statusCode, message) ApiResponse
    }

    %% Relationships across Security and Exception Infrastructure
    SecurityConfig --> JwtAuthenticationFilter : configures
    JwtAuthenticationFilter --> JwtService : validatesUsing
    JwtAuthenticationFilter ..> CasmsPrincipal : authenticatesAs
    CasmsPermissionEvaluator ..> CasmsPrincipal : evaluates
    GlobalExceptionHandler ..> BusinessException : intercepts
    GlobalExceptionHandler ..> ResourceNotFoundException : intercepts
    GlobalExceptionHandler ..> ApiResponse : producesResponse
```
