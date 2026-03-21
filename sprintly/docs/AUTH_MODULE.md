# 🔐 Auth Module — Technical Documentation

**Module:** `sprintly-auth`  
**Base Path:** `/api/auth`  
**Phase:** 2

---

## Overview

The Auth module handles all identity and access management for Sprintly.
It provides email/password authentication, JWT token issuance,
token refresh with rotation, and logout.

---

## Architecture

```
POST /api/auth/register ──► AuthController ──► AuthService
POST /api/auth/login    ──►      │          ──► AuthenticationManager
POST /api/auth/refresh  ──►      │          ──► JwtService
POST /api/auth/logout   ──►      │          ──► RefreshTokenRepository

Every other request ──► JwtAuthFilter ──► CustomUserDetailsService
                                      ──► SecurityContextHolder
```

---

## JWT Token Design

### Access Token
| Property  | Value           |
|-----------|-----------------|
| Algorithm | HS256           |
| Expiry    | 15 minutes      |
| Claims    | sub (email), userId, type=access, iat, exp |
| Usage     | `Authorization: Bearer <token>` on every API call |

### Refresh Token
| Property  | Value           |
|-----------|-----------------|
| Algorithm | HS256           |
| Expiry    | 7 days          |
| Claims    | sub (email), type=refresh, iat, exp |
| Storage   | Persisted in `refresh_tokens` table |
| Usage     | `POST /api/auth/refresh` body only |

---

## Token Rotation

Every `/refresh` call:
1. Validates the submitted refresh token (exists, not revoked, not expired)
2. Marks it as `revoked = true`
3. Issues a **new** refresh token
4. Issues a **new** access token

This sliding window means:
- Active users never get logged out
- Stolen refresh tokens become invalid after the first legitimate use
- If an attacker uses a stolen token first, the real user's next call fails

---

## Database Schema

```sql
-- users table (owned by sprintly-user module, referenced here)
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100)  NOT NULL,
    email           VARCHAR(150)  NOT NULL UNIQUE,
    password        VARCHAR(255)  NOT NULL,
    enabled         BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP     NOT NULL,
    updated_at      TIMESTAMP
);

-- refresh_tokens table (owned by sprintly-auth module)
CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    token       VARCHAR(500) NOT NULL UNIQUE,
    user_id     BIGINT       NOT NULL REFERENCES users(id),
    expires_at  TIMESTAMP    NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
```

---

## API Reference

### POST /api/auth/register

**Request:**
```json
{
  "name": "Ravi Kumar",
  "email": "ravi@sprintly.com",
  "password": "MyPass@123"
}
```

**Response 201:**
```json
{
  "success": true,
  "message": "User registered successfully",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "userId": 1,
    "email": "ravi@sprintly.com",
    "name": "Ravi Kumar"
  },
  "timestamp": "2025-01-01T10:00:00"
}
```

**Error 400 (duplicate email):**
```json
{
  "success": false,
  "status": 400,
  "error": "Bad Request",
  "message": "An account with email 'ravi@sprintly.com' already exists"
}
```

---

### POST /api/auth/login

**Request:**
```json
{
  "email": "ravi@sprintly.com",
  "password": "MyPass@123"
}
```

**Response 200:** Same as register response.

**Error 401:**
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid email or password"
}
```

---

### POST /api/auth/refresh

**Request:**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**Response 200:** New access + refresh token pair.

---

### POST /api/auth/logout

**Headers:** `Authorization: Bearer <accessToken>`

**Response 200:**
```json
{
  "success": true,
  "message": "Logged out successfully from all devices"
}
```

---

## Security Configuration

### Public Routes (no JWT required)
```
POST /api/auth/**
GET  /swagger-ui/**
GET  /v3/api-docs/**
GET  /actuator/health

---

## Design Patterns in This Module

### Singleton (AppConfigManager)
```java
// Access shared config without Spring injection
int maxTokensPerUser = AppConfigManager.getInstance().getInt("app.max-tokens-per-user", 5);
```

### Builder (AuthResponse, ApiResponse)
```java
return AuthResponse.builder()
    .accessToken(accessToken)
    .refreshToken(refreshToken)
    .expiresIn(900)
    .userId(user.getId())
    .email(user.getEmail())
    .name(user.getName())
    .build();
```

---

## Environment Variables

| Variable              | Description                    | Default (dev only!)          |
|-----------------------|--------------------------------|------------------------------|
| `JWT_SECRET`          | HMAC-SHA256 secret (min 32ch)  | sprintly-super-secret-key... |

> ⚠️ **Never commit real secrets.** Use environment variables or a secrets manager in production.

---

## Files in This Module

```
sprintly-auth/
├── pom.xml
└── src/
    ├── main/java/com/sprintly/auth/
    │   ├── controller/
    │   │   └── AuthController.java       ← REST endpoints + Swagger docs
    │   ├── dto/
    │   │   ├── RegisterRequest.java      ← @Valid input DTO
    │   │   ├── LoginRequest.java         ← @Valid input DTO
    │   │   ├── RefreshTokenRequest.java  ← @Valid input DTO
    │   │   └── AuthResponse.java         ← Builder output DTO
    │   ├── entity/
    │   │   └── RefreshToken.java         ← JPA entity for token storage
    │   ├── repository/
    │   │   └── RefreshTokenRepository.java
    │   ├── security/
    │   │   ├── SecurityConfig.java       ← Spring Security + JWT wiring
    │   │   ├── JwtAuthFilter.java        ← Per-request JWT validation
    │   └── service/
    │       ├── AuthService.java          ← register/login/refresh/logout logic
    │       ├── JwtService.java           ← JWT generation + parsing
    │       └── CustomUserDetailsService.java ← Spring Security user loading
    └── test/java/com/sprintly/auth/
        └── service/
            └── AuthServiceTest.java      ← Unit tests (Mockito)
```
