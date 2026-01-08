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

---

## 📦 Tech Stack

- Java 21  
- Spring Boot 3  
- Spring Security (Resource Server)  
- Spring WebFlux WebClient  
- Keycloak 26+  
- Bucket4j Spring Boot Starter  
- JUnit 5 + WebTestClient  
- Docker Compose  

---

## 🚀 Running the Project

### 1. Start Keycloak

```bash
docker compose up -d
```

Keycloak will be available at:

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
        |  client_id/secret     |                           |
        |---------------------->|                           |
        |                       |  /token, /logout          |
        |                       |-------------------------->|
        |                       |                           |
```

## Authentication flow

```
Client
  |
  | POST /auth/login
  | { username, password, clientId, clientSecret }
  v
Spring Boot Proxy
  |
  | POST /realms/.../token
  v
Keycloak
  |
  | access_token + refresh_token
  v
Spring Boot Proxy
  |
  | returns tokens to client
  v
Client
```

---

# 🔐 API Endpoints

## 1. Login
`POST /auth/login`

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
  "access_token": "...",
  "refresh_token": "...",
  "expires_in": 300,
  "refresh_expires_in": 1800
}
```

---

## 2. Refresh Token
`POST /auth/refresh`

```json
{
  "refreshToken": "eyJhbGciOi...",
  "clientId": "spring-app",
  "clientSecret": "CHANGE_ME"
}
```

---

## 3. Logout
`POST /auth/logout`

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
Requires role: **USER**

### `/api/admin`
Requires role: **ADMIN**

Example:

```
GET /api/user
Authorization: Bearer <access_token>
```

---

# 🚦 Rate Limiting (Bucket4j, configuration‑only)

The project supports **fully configurable rate limiting** using  
**Bucket4j Spring Boot Starter**, without any custom Java code.

Rate limits are defined entirely in `application.properties`, allowing you to:

- limit any endpoint
- set different limits per endpoint
- configure limits per clientId, IP, or request expression
- enable/disable rate limiting without code changes

### 📌 Example: Limit `/auth/login` to 5 requests per minute

```properties
bucket4j.enabled=true

bucket4j.filters[0].cache-name=rate-limit-cache
bucket4j.filters[0].url=/auth/login
bucket4j.filters[0].rate-limits[0].bandwidths[0].capacity=5
bucket4j.filters[0].rate-limits[0].bandwidths[0].refill-capacity=5
bucket4j.filters[0].rate-limits[0].bandwidths[0].refill-period=1m
```

### 📌 Example: Limit `/api/admin` to 20 requests per minute

```properties
bucket4j.filters[1].cache-name=rate-limit-cache
bucket4j.filters[1].url=/api/admin
bucket4j.filters[1].rate-limits[0].bandwidths[0].capacity=20
bucket4j.filters[1].rate-limits[0].bandwidths[0].refill-capacity=20
bucket4j.filters[1].rate-limits[0].bandwidths[0].refill-period=1m
```

### 📌 Example: Rate limit based on clientId

```properties
bucket4j.filters[2].url=/auth/login
bucket4j.filters[2].rate-limits[0].expression=clientId == 'spring-app'
bucket4j.filters[2].rate-limits[0].bandwidths[0].capacity=3
bucket4j.filters[2].rate-limits[0].bandwidths[0].refill-capacity=3
bucket4j.filters[2].rate-limits[0].bandwidths[0].refill-period=1m
```

### ✔ No Java code required

The starter automatically:

- registers filters
- applies Bucket4j rules
- handles throttling responses
- logs rate limit violations

Your application stays clean and configuration‑driven.

---

# 🧪 Integration Tests

Integration tests verify:

- login
- refresh
- logout
- role-based access
- JWT validation
- Keycloak integration

Run:

```bash
mvn test
```

---

# 🧱 Project Structure

```
src/main/java
 └── lt/satsyuk
      ├── auth
      │    ├── AuthController.java
      │    ├── KeycloakAuthService.java
      │    ├── LoginRequest.java
      │    ├── RefreshRequest.java
      │    ├── LogoutRequest.java
      │    └── KeycloakTokenResponse.java
      ├── security
      │    └── SecurityConfig.java
      └── JwtDemoApplication.java

src/test/java
 └── lt/satsyuk
      └── KeycloakIntegrationIT.java
```

---

# 🛠 Troubleshooting

### ❌ `Could not resolve placeholder 'keycloak.token-url'`
**Cause:** property missing or misnamed.  
**Fix:** ensure:

```properties
keycloak.token-url=...
```

---

### ❌ `WebClient` not found
Add dependency:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

---

### ❌ Lombok annotations not working
Enable annotation processing:

```
Settings → Build → Compiler → Annotation Processors → Enable
```

---

### ❌ 403 on `/api/user` or `/api/admin`
Ensure Keycloak roles are inside:

```json
"realm_access": { "roles": ["USER"] }
```

---

# 🎨 Adding a Frontend

Any frontend (React, Vue, Angular, mobile) can integrate easily.

### Frontend responsibilities:

1. Collect:
   - username
   - password
   - clientId
   - clientSecret

2. Send them to `/auth/login`

3. Store:
   - access_token
   - refresh_token

4. Attach access_token to every request:

```
Authorization: Bearer <token>
```

5. Refresh token when needed
6. Call `/auth/logout` on logout

### Example (React)

```js
const login = async () => {
  const res = await fetch("http://localhost:8081/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      username,
      password,
      clientId: "spring-app",
      clientSecret: "CHANGE_ME"
    })
  });

  const tokens = await res.json();
  localStorage.setItem("access", tokens.access_token);
  localStorage.setItem("refresh", tokens.refresh_token);
};
```

---

# 📄 License

MIT (or any license you prefer).
```
