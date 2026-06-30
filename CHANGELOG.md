# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Security
- Bumped `spring.boot.version` from `4.0.0` to `4.0.3` for dependency remediation via the Spring Boot BOM
- Picked up newer BOM-managed versions of Tomcat, Logback, and AssertJ without changing application behavior

### Added
- Optional `idempotencyKey` field on `POST /api/clients` — when provided, it becomes the Request id, enabling clients to safely retry requests; returns `409 Conflict` if the same key is reused with a different payload
- Optional `idempotencyKey` field on `POST /api/accounts/balance/pessimistic` and `POST /api/accounts/balance/optimistic` — balance update requests are now tracked in the `request` table with status lifecycle (PENDING → COMPLETED|FAILED), supporting idempotent replays
- Spring Boot + Keycloak OAuth2 proxy with dynamic client credentials
- Opaque token introspection via Spring Security resource server
- Role-based authorization (ADMIN, CLIENT_CREATE, CLIENT_GET, CLIENT_SEARCH, UPDATE_BALANCE)
- New protected endpoint `GET /api/clients/search?q=...` (requires role `CLIENT_SEARCH`) for client lookup by first/last name with minimum query length 3
- Seed SQL script `src/main/resources/db/seed/clients_1000_fake.sql` to generate 1000 fictitious clients and zero-balance accounts
- Client endpoints (`POST /api/clients` for async request submission, `GET /api/clients/{id}`)
- Request status endpoint (`GET /api/requests/{id}`)
- Account endpoints (`GET /api/accounts/client/{clientId}`, `POST /api/accounts/balance/pessimistic`, `POST /api/accounts/balance/optimistic`)
- Authentication endpoints (login, refresh, logout)
- Rate limiting with Bucket4j (login: 5 req/min, clients: 20 req/min)
- HTTP response header `X-Trace-Id` for request/trace correlation, including error responses (`401`, `403`, `429`)
- Internationalization (i18n) — English and Russian
- Flyway database migrations
- OpenTelemetry tracing with OTLP exporter
- Structured JSON logging with Logstash encoder and trace/span IDs
- Prometheus metrics via Spring Boot Actuator
- Swagger/OpenAPI documentation (SpringDoc)
- Full observability stack via Docker Compose (Grafana, Prometheus, Tempo, Loki)
- Comprehensive integration tests with Testcontainers (Keycloak, PostgreSQL)
- Negative testing with WireMock (network failures, timeouts, error responses)
- Phone uniqueness validation with conflict handling
- Custom exception handling with GlobalExceptionHandler
- Automatic Keycloak realm import (users, roles, protocol mappers)
- Postman collection for manual API testing
- Multi-stage Docker build with non-root user
- JaCoCo code coverage reports (unit + integration)
- CI/CD pipeline with GitHub Actions and SonarCloud

