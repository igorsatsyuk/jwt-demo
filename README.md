```markdown
# 🔐 Spring Boot + Keycloak OAuth2 Proxy  
Dynamic authentication with client-provided `client_id` and `client_secret`

This project is a lightweight but production-ready backend that acts as an **OAuth2 proxy in front of Keycloak**.  
The backend does **not** store `client_id` or `client_secret`.  
Instead, the client sends them in each authentication request, making the system flexible and multi-tenant.

Supported features:
- 🔑 Username/password login  
- 🔄 Token refresh  
- 🚪 Logout (refresh token revocation)  
- 🛡 JWT validation via Spring Security  
- 🎭 Role-based authorization (`USER`, `ADMIN`)  
- 🧪 Full integration test suite  

---

## 📦 Tech Stack

- **Java 21**
- **Spring Boot 3**
- **Spring Security (Resource Server)**
- **Spring WebFlux WebClient**
- **Keycloak 26+**
- **JUnit 5 + WebTestClient**
- **Docker Compose**

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

## Authorization flow

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

# 🧪 Integration Tests

Tests verify:

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

### ❌ Error: `Could not resolve placeholder 'keycloak.token-url'`
**Cause:** Missing property in `application.properties`.

**Fix:**

```properties
keycloak.token-url=http://localhost:8080/realms/my-realm/protocol/openid-connect/token
```

---

### ❌ `WebClient` not found
**Cause:** Missing dependency.

**Fix:**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

---

### ❌ Lombok annotations not working
**Fix:**

- Install Lombok plugin in IntelliJ
- Enable annotation processing:  
  `Settings → Build → Compiler → Annotation Processors → Enable`

---

### ❌ 403 on `/api/user` or `/api/admin`
**Cause:** Keycloak roles are inside `realm_access.roles`.

**Fix:** Use custom JWT converter.

---

### ❌ Refresh token fails after logout
This is expected — Keycloak revokes refresh tokens on logout.

---

# 🎨 How to Add a Frontend

You can integrate **any frontend** (React, Vue, Angular, mobile apps, desktop apps).

### Frontend responsibilities:

1. Collect:
    - username
    - password
    - clientId
    - clientSecret

2. Send them to:

```
POST /auth/login
```

3. Store:
    - access_token
    - refresh_token

4. Attach access_token to every request:

```
Authorization: Bearer <token>
```

5. When access_token expires:
    - call `/auth/refresh`

6. When user logs out:
    - call `/auth/logout`

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