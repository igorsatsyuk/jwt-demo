```markdown
# 🔐 Spring Boot + Keycloak OAuth2 Proxy  
Dynamic authentication with client-provided `client_id` and `client_secret`

This project implements a clean, production-ready OAuth2 proxy in front of Keycloak.  
The backend does **not** store `client_id` or `client_secret`.  
Instead, the client sends them in each authentication request, making the system flexible, multi-tenant, and secure.

Supported features:
- 🔑 Username/password login  
- 🔄 Token refresh  
- 🚪 Logout (refresh token revocation)  
- 🛡 JWT validation via Spring Security  
- 🎭 Role-based authorization (`USER`, `ADMIN`)  
- 🚦 Configurable rate limiting (Bucket4j)  
- 🧪 Full integration test suite  
- 📦 Automatic Keycloak realm import (users, roles, mappers)

---

## 📦 Tech Stack

- Java 17  
- Spring Boot 3.2  
- Spring Security (Resource Server)  
- Spring Web (REST)  
- Keycloak 26+  
- Bucket4j Spring Boot Starter  
- JUnit 5 + TestRestTemplate  
- Docker Compose  

---

## 🚀 Running the Project

### 1. Start Keycloak (with automatic realm import)

```bash
docker compose up -d
```

Keycloak automatically imports:

- realm `my-realm`
- users (`user`, `admin`)
- roles (`USER`, `ADMIN`)
- client `spring-app`
- protocol mappers (roles → access_token)

Keycloak UI:

```
http://localhost:8080
```

### 2. Start Spring Boot

```bash
mvn spring-boot:run
```

Application runs at:

```
http://localhost:8081
```

---

## ⚙️ Configuration (`application.properties`)

```properties
server.port=8081

keycloak.realm=my-realm
keycloak.auth-server-url=http://localhost:8080

keycloak.token-url=${keycloak.auth-server-url}/realms/${keycloak.realm}/protocol/openid-connect/token
keycloak.logout-url=${keycloak.auth-server-url}/realms/${keycloak.realm}/protocol/openid-connect/logout

spring.security.oauth2.resourceserver.jwt.issuer-uri=${keycloak.auth-server-url}/realms/${keycloak.realm}
```

The backend **does not store** any client credentials.  
All credentials are provided dynamically by the client.

---

# 🧩 Architecture

## High-level flow

```
+-------------+        +-------------------+        +----------------+
|   Client    | -----> | Spring Boot Proxy | -----> |   Keycloak     |
| (Frontend)  |        |  (This project)   |        | Auth Server    |
+-------------+        +-------------------+        +----------------+
        |                       |                           |
        |  username/password    |                           |
        |  clientId/secret      |                           |
        |---------------------->|                           |
        |                       |  /token, /logout          |
        |                       |-------------------------->|
        |                       |                           |
```

---

# 🛡 Security Architecture

The project uses a **clean, layered security architecture** combining:

- Keycloak for authentication and role assignment
- Spring Security for JWT validation
- Method-level authorization via `@PreAuthorize`
- A custom `KeycloakRoleConverter` for mapping Keycloak roles to Spring authorities

This ensures a clear separation of responsibilities:

| Layer | Responsibility |
|-------|----------------|
| **Keycloak** | Authentication, issuing tokens, storing users, roles, and mappers |
| **Spring Security** | Validating JWT, extracting authorities, enforcing access rules |
| **Controllers** | Declaring authorization rules via annotations |

---

## 🔐 Authentication Flow

1. Client sends username/password + clientId/clientSecret to `/api/auth/login`
2. Backend forwards credentials to Keycloak `/token`
3. Keycloak returns:
    - access_token
    - refresh_token
4. Backend returns tokens to the client
5. Client uses access_token for all protected endpoints

---

## 📊 Sequence Diagram (Login / Refresh / Logout)

```text
===========================================================
                 LOGIN FLOW
===========================================================

Client
  |
  | 1. POST /api/auth/login
  |    { username, password, clientId, clientSecret }
  v
Spring Boot (AuthController)
  |
  | 2. KeycloakAuthService.login()
  v
Keycloak
  |
  | 3. POST /realms/my-realm/protocol/openid-connect/token
  |      grant_type=password
  |      username, password
  |      client_id, client_secret
  |
  | 4. 200 OK
  |      { access_token, refresh_token }
  v
Spring Boot
  |
  | 5. Wrap into ApiResponse
  v
Client


===========================================================
                 REFRESH FLOW
===========================================================

Client
  |
  | 1. POST /api/auth/refresh
  |    { refreshToken, clientId, clientSecret }
  v
Spring Boot
  |
  | 2. KeycloakAuthService.refresh()
  v
Keycloak
  |
  | 3. POST /realms/my-realm/protocol/openid-connect/token
  |      grant_type=refresh_token
  |      refresh_token
  |      client_id, client_secret
  |
  | 4. 200 OK
  |      { new_access_token, new_refresh_token }
  v
Spring Boot
  |
  | 5. Wrap into ApiResponse
  v
Client


===========================================================
                 LOGOUT FLOW
===========================================================

Client
  |
  | 1. POST /api/auth/logout
  |    { refreshToken, clientId, clientSecret }
  v
Spring Boot
  |
  | 2. KeycloakAuthService.logout()
  v
Keycloak
  |
  | 3. POST /realms/my-realm/protocol/openid-connect/logout
  |      client_id, client_secret
  |      refresh_token
  |
  | 4. 200 OK (always)
  v
Spring Boot
  |
  | 5. Return ApiResponse(success=true)
  v
Client
```

---

# 🎭 Role Model

## Roles in Keycloak

| Role  | Description |
|-------|-------------|
| `USER`  | Standard application user |
| `ADMIN` | Administrative user |

Assignments:

- `user` → USER
- `admin` → ADMIN

---

## Role Mapping

Keycloak → Spring Security:

```
USER  → ROLE_USER  
ADMIN → ROLE_ADMIN
```

---

## Access Matrix

| User     | `/api/user` | `/api/admin` |
|----------|-------------|--------------|
| user     | ✅ Allowed   | ❌ Forbidden |
| admin    | ❌ Forbidden | ✅ Allowed   |

---

## Extending the Model

To add new roles:

1. Create a realm role in Keycloak
2. Assign it to users
3. Protect endpoints:

```java
@PreAuthorize("hasRole('MANAGER')")
```

No changes required in SecurityConfig.

---

# 🔐 API Endpoints

## 1. Login
`POST /api/auth/login`

```json
{
  "username": "user",
  "password": "password",
  "clientId": "spring-app",
  "clientSecret": "CHANGE_ME"
}
```

---

## 2. Refresh Token
`POST /api/auth/refresh`

```json
{
  "refreshToken": "...",
  "clientId": "spring-app",
  "clientSecret": "CHANGE_ME"
}
```

---

## 3. Logout
`POST /api/auth/logout`

```json
{
  "refreshToken": "...",
  "clientId": "spring-app",
  "clientSecret": "CHANGE_ME"
}
```

---

# 🛡 Protected Endpoints

### `/api/user`
Requires: `ROLE_USER`

### `/api/admin`
Requires: `ROLE_ADMIN`

---

# 🚦 Rate Limiting (Bucket4j)

Rate limits are defined entirely in `application.properties`.

Example:

```properties
bucket4j.filters[0].url=/api/auth/login
bucket4j.filters[0].rate-limits[0].bandwidths[0].capacity=5
bucket4j.filters[0].rate-limits[0].bandwidths[0].refill-period=1m
```

---

# 🧪 Integration Tests

Integration tests verify:

- login
- refresh
- logout
- role-based access
- JWT validation
- Keycloak integration
- protected endpoints

Run:

```bash
mvn test
```

---

# 🧱 Project Structure

```
C:.
├── docker-compose.yaml
├── pom.xml
├── README.md
├── keycloak/
│   └── realm-export.json
├── postman/
│   └── My Collection.postman_collection.json
└── src
    ├── main
    │   ├── java
    │   │   └── lt
    │   │       └── satsyuk
    │   │           ├── MainApplication.java
    │   │           ├── api
    │   │           │   ├── AuthController.java
    │   │           │   ├── DemoController.java
    │   │           │   └── dto
    │   │           │       └── ApiResponse.java
    │   │           ├── auth
    │   │           │   ├── JsonAuthEntryPoint.java
    │   │           │   ├── KeycloakAuthService.java
    │   │           │   ├── KeycloakProperties.java
    │   │           │   └── dto
    │   │           │       ├── KeycloakTokenResponse.java
    │   │           │       ├── LoginRequest.java
    │   │           │       ├── LogoutRequest.java
    │   │           │       └── RefreshRequest.java
    │   │           ├── config
    │   │           │   ├── RestTemplateConfig.java
    │   │           │   └── SecurityConfig.java
    │   │           ├── exception
    │   │           │   ├── GlobalExceptionHandler.java
    │   │           │   └── KeycloakAuthException.java
    │   │           └── security
    │   │               └── KeycloakRoleConverter.java
    │   └── resources
    │       └── application.properties
    └── test
        └── java
            └── lt
                └── satsyuk
                    └── api
                        ├── integrationtest
                        │   ├── KeycloakIntegrationIT.java
                        │   └── TestSupport.java
                        └── unittest
```

---

# 🛠 Troubleshooting

### ❌ 403 on `/api/user` or `/api/admin`
Check token contains:

```json
"realm_access": { "roles": ["USER"] }
```

If missing → check Keycloak mappers.

---

### ❌ Logout always returns 200
Keycloak 26 always returns 200 for `/logout`.  
Your API wraps this into a structured response.

---

# 📄 License

MIT (or any license you prefer).
```
