# Implementation plan

Spring Boot 3.3.4 MVC / JPA / Flyway / PostgreSQL; Java 21; Maven 3.9.16 available, no wrapper present. Tests use existing JUnit/Mockito through starter-test. PostgreSQL major remains UNKNOWN.

1. Actor-scoped recovery with guest quote proof and current-state reconstruction.
2. Booking reservation and completion share booking transaction, PostgreSQL uniqueness also protects anonymous keys. Legacy conflicts require manual reconciliation.
3. Customer identity preservation, bounded physical signature metadata, verified payment and installment projection.
4. Transactional JDBC outbox plus SMTP worker, and hashed single-use password setup for newly provisioned guest accounts.
5. Focused tests, source review and API sequence coverage; no shared database/migration execution.

Dependency justification: spring-boot-starter-mail supplies the supported SMTP sender; no hand-rolled SMTP or credential storage in source. Credentials come from deployment environment.
