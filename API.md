# API Reference

Base URL: `http://localhost:8081`

Swagger UI: `http://localhost:8081/swagger-ui/index.html`  
OpenAPI JSON: `http://localhost:8081/v3/api-docs`

---

## Response Format

All endpoints return a standardized response:

```json
{
  "code": 0,
  "message": "Success",
  "data": { ... }
}
```

Error response:

```json
{
  "code": 400,
  "message": "Error description",
  "data": null
}
```

### Response Headers

Every response may include correlation header:

```http
X-Trace-Id: <trace-id>
```

Use this value to correlate client-side errors with server logs/traces (including `401`, `403`, and `429` responses).

### Error Codes

| Code | Meaning               |
|------|-----------------------|
| 0    | Success               |
| 400  | Bad Request           |
| 401  | Unauthorized          |
| 403  | Forbidden             |
| 404  | Not Found             |
| 409  | Conflict              |
| 429  | Too Many Requests     |
| 500  | Internal Server Error |

---

## Authentication Endpoints

DPoP support:
- Auth endpoints accept optional header `DPoP: <proof-jwt>` and forward it to Keycloak.
- Protected endpoints support both:
  - `Authorization: Bearer <access_token>`
  - `Authorization: DPoP <access_token>` + `DPoP: <proof-jwt>`
- If token introspection contains `cnf.jkt`, DPoP proof is mandatory.

### POST /api/auth/login

Authenticate with username/password and client credentials.

**Optional Header:**
```http
DPoP: <proof-jwt>
```

**Request Body:**
```json
{
  "username": "user",
  "password": "password",
  "clientId": "spring-app",
  "clientSecret": "your-client-secret"
}
```

**Success Response (200):**
```json
{
  "code": 0,
  "message": "Success",
  "data": {
    "access_token": "eyJhbG...",
    "refresh_token": "eyJhbG...",
    "token_type": "Bearer",
    "expires_in": 300
  }
}
```

**Error Responses:**
- `400` — Validation error (missing fields)
- `401` — Invalid credentials
- `429` — Rate limit exceeded (5 requests/minute)

---

### POST /api/auth/refresh

Refresh an expired access token.

**Optional Header:**
```http
DPoP: <proof-jwt>
```

**Request Body:**
```json
{
  "refreshToken": "eyJhbG...",
  "clientId": "spring-app",
  "clientSecret": "your-client-secret"
}
```

**Success Response (200):**
```json
{
  "code": 0,
  "message": "Success",
  "data": {
    "access_token": "eyJhbG...",
    "refresh_token": "eyJhbG...",
    "token_type": "Bearer",
    "expires_in": 300
  }
}
```

**Error Responses:**
- `400` — Validation error
- `401` — Invalid or expired refresh token

---

### POST /api/auth/logout

Revoke a refresh token.

**Optional Header:**
```http
DPoP: <proof-jwt>
```

**Request Body:**
```json
{
  "refreshToken": "eyJhbG...",
  "clientId": "spring-app",
  "clientSecret": "your-client-secret"
}
```

**Success Response (200):**
```json
{
  "code": 0,
  "message": "Logout successful",
  "data": null
}
```

> ℹ️ Keycloak 26 always returns 200 for logout, even with invalid tokens.

---

## Client Endpoints (Protected)

All client endpoints require a valid access token in the `Authorization` header:

```
Authorization: Bearer <access_token>
```

Or DPoP:

```
Authorization: DPoP <access_token>
DPoP: <proof-jwt>
```

### POST /api/clients

Accept a new asynchronous client creation request.

**Required Role:** `CLIENT_CREATE`

**Request Body:**
```json
{
  "idempotencyKey": "2e6a42a8-8bb7-4f7d-b4d6-71eb31ec8a13",
  "firstName": "John",
  "lastName": "Doe",
  "phone": "+37061234567"
}
```

**Validation Rules:**

| Field          | Rules                                                           |
|----------------|-----------------------------------------------------------------|
| idempotencyKey | Optional UUID; when provided, used as the Request id            |
| firstName      | Required, max 100 characters                                    |
| lastName       | Required, max 100 characters                                    |
| phone          | Required, must match pattern `\+?\d{7,15}` (optional `+` prefix) |

**Success Response (202):**
```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "requestId": "2e6a42a8-8bb7-4f7d-b4d6-71eb31ec8a13",
    "status": "PENDING"
  }
}
```

After that, poll `GET /api/requests/{requestId}` until the request reaches `COMPLETED` or `FAILED`.

**Error Responses:**
- `400` — Validation error
- `401` — Missing or invalid token
- `403` — Insufficient role
- `409` — Idempotency key conflict (same key, different payload)
- `429` — Rate limit exceeded (20 requests/minute)

---

### GET /api/requests/{id}

Get asynchronous request status and final response payload when processing is complete.

**Required Role:** `CLIENT_CREATE`

**Path Parameters:**

| Parameter | Type | Description        |
|-----------|------|--------------------|
| id        | UUID | Request identifier |

**Success Response (200), still processing:**
```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "requestId": "2e6a42a8-8bb7-4f7d-b4d6-71eb31ec8a13",
    "type": "CLIENT_CREATE",
    "status": "PROCESSING",
    "createdAt": "2026-03-19T12:00:00Z",
    "statusChangedAt": "2026-03-19T12:00:01Z",
    "response": null
  }
}
```

**Success Response (200), processed:**
```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "requestId": "2e6a42a8-8bb7-4f7d-b4d6-71eb31ec8a13",
    "type": "CLIENT_CREATE",
    "status": "COMPLETED",
    "createdAt": "2026-03-19T12:00:00Z",
    "statusChangedAt": "2026-03-19T12:00:02Z",
    "response": {
      "code": 0,
      "message": "OK",
      "data": {
        "id": 1,
        "firstName": "John",
        "lastName": "Doe",
        "phone": "+37061234567"
      }
    }
  }
}
```

**Error Responses:**
- `400` — Invalid UUID format
- `401` — Missing or invalid token
- `403` — Insufficient role
- `404` — Request not found

---

### GET /api/clients/{id}

Get a client by ID.

**Required Role:** `CLIENT_GET`

**Path Parameters:**

| Parameter | Type | Description       |
|-----------|------|-------------------|
| id        | Long | Client identifier |

**Success Response (200):**
```json
{
  "code": 0,
  "message": "Success",
  "data": {
    "id": 1,
    "firstName": "John",
    "lastName": "Doe",
    "phone": "+37061234567"
  }
}
```

**Error Responses:**
- `400` — Invalid ID format
- `401` — Missing or invalid token
- `403` — Insufficient role
- `404` — Client not found

---

### GET /api/clients/search?q={query}

Search clients by first name or last name (case-insensitive).

**Required Role:** `CLIENT_SEARCH`

**Query Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| q         | String | Search term, minimum 3 characters |

Returned rows are limited by configuration property `app.clients.search.max-results`.

**Success Response (200):**
```json
{
  "code": 0,
  "message": "OK",
  "data": [
    {
      "id": 1,
      "firstName": "Alice",
      "lastName": "Brown",
      "phone": "+37070000001"
    }
  ]
}
```

**Error Responses:**
- `400` — Query shorter than 3 characters
- `401` — Missing or invalid token
- `403` — Insufficient role

---

## Account Endpoints (Protected)

### POST /api/accounts/balance/pessimistic

Update account balance using pessimistic locking.

**Required Role:** `UPDATE_BALANCE`

**Request Body:**
```json
{
  "clientId": 1,
  "amount": 100.50
}
```

**Success Response (200):**
```json
{
  "code": 0,
  "message": "Success",
  "data": {
    "accountId": 1,
    "clientId": 1,
    "balance": 150.50
  }
}
```

**Error Responses:**
- `400` — Validation error
- `401` — Missing or invalid token
- `403` — Insufficient role
- `404` — Account not found

---

### POST /api/accounts/balance/optimistic

Update account balance using optimistic locking with retries.

**Required Role:** `UPDATE_BALANCE`

**Request Body:**
```json
{
  "clientId": 1,
  "amount": -50.00
}
```

**Success Response (200):**
```json
{
  "code": 0,
  "message": "Success",
  "data": {
    "accountId": 1,
    "clientId": 1,
    "balance": 100.50
  }
}
```

**Error Responses:**
- `400` — Validation error
- `401` — Missing or invalid token
- `403` — Insufficient role
- `404` — Account not found
- `409` — Optimistic lock conflict

---

### GET /api/accounts/client/{clientId}

Get account by client id.

**Required Role:** `CLIENT_GET`

**Path Parameters:**

| Parameter | Type | Description       |
|-----------|------|-------------------|
| clientId  | Long | Client identifier |

**Success Response (200):**
```json
{
  "code": 0,
  "message": "Success",
  "data": {
    "accountId": 1,
    "clientId": 1,
    "balance": 100.50
  }
}
```

**Error Responses:**
- `400` — Invalid ID format
- `401` — Missing or invalid token
- `403` — Insufficient role
- `404` — Account not found

---

## Internationalization

The API supports multiple languages via the `Accept-Language` header.

**Supported Languages:**

| Code | Language |
|------|----------|
| `en` | English  |
| `ru` | Russian  |

**Example:**
```http
GET /api/clients/999999 HTTP/1.1
Authorization: Bearer <token>
Accept-Language: ru
```

Response:
```json
{
  "code": 404,
  "message": "Клиент с id=999999 не найден",
  "data": null
}
```

---

## Actuator Endpoints (Monitoring)

| Endpoint                       | Description              |
|--------------------------------|--------------------------|
| `GET /actuator/health`         | Application health check |
| `GET /actuator/prometheus`     | Prometheus metrics       |

---

## Rate Limits

| Endpoint          | Limit              |
|-------------------|--------------------|
| `/api/auth/login` | 5 requests/minute  |
| `/api/clients.*`  | 20 requests/minute |

When rate limit is exceeded, the API returns HTTP `429 Too Many Requests`.

Typical response headers for a throttled request include `X-Trace-Id`, which can be used for diagnostics:

```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/json
X-Trace-Id: 2f0a3e58a2d7f97c3f6d9d6cc2b1aa93
```
