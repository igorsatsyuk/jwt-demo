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

Response:

```json
{
  "data": {
    "access_token": "...",
    "refresh_token": "...",
    "expires_in": 300,
    "refresh_expires_in": 1800
  }
}
```

---

## 2. Refresh Token
`POST /api/auth/refresh`

```json
{
  "refreshToken": "eyJhbGciOi...",
  "clientId": "spring-app",
  "clientSecret": "CHANGE_ME"
}
```

---

## 3. Logout
`POST /api/auth/logout`

```json
{
  "refreshToken": "eyJhbGciOi...",
  "clientId": "spring-app",
  "clientSecret": "CHANGE_ME"
}
```

---

# 🛡 Protected Endpoints

### `/api/user`
Requires role: **USER** or **ADMIN**

### `/api/admin`
Requires role: **ADMIN**

Example:

```
GET /api/user
Authorization: Bearer <access_token>
```

---

# 🚦 Rate Limiting (Bucket4j)

Rate limits are defined entirely in `application.properties`.

### Example: Limit `/api/auth/login` to 5 requests per minute

```properties
bucket4j.enabled=true

bucket4j.filters[0].cache-name=rate-limit-cache
bucket4j.filters[0].url=/api/auth/login
bucket4j.filters[0].rate-limits[0].bandwidths[0].capacity=5
bucket4j.filters[0].rate-limits[0].bandwidths[0].refill-capacity=5
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
- protected endpoints (`/api/user`, `/api/admin`)

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
Ensure access_token contains:

```json
"realm_access": { "roles": ["USER"] }
"resource_access": { "spring-app": { "roles": ["USER"] } }
```

If missing → check Keycloak mappers.

---

### ❌ Logout always returns 200
Keycloak 26 **always** returns 200 for `/logout`, even for invalid tokens.  
Your API wraps this into a structured error.

---

# 📄 License

MIT (or any license you prefer).
```
