# 🚀 CLAUDE.md — Sprintly Project Master Reference

> Single source of truth for the entire Sprintly project.
> Read this before making any changes to any module.

---

## 📌 Project Identity

| Field        | Value                                                                 |
|--------------|-----------------------------------------------------------------------|
| Name         | **Sprintly**                                                          |
| Description  | ⚡ Sprintly — Real-time collaborative task management backend. Built with Spring Boot 3, JWT Auth, WebSocket, multi-module Maven architecture and clean LLD design patterns. |
| Java Version | 17 (LTS)                                                              |
| Spring Boot  | 3.2.4                                                                 |
| Build Tool   | Maven Multi-module                                                    |
| Entry Point  | `sprintly-gateway` — the ONLY runnable `@SpringBootApplication`       |
| GitHub Repo  | `sprintly`                                                            |

---

## 🗂️ Module Map

```
sprintly/
├── sprintly-common       ← Shared DTOs, exceptions, enums, patterns (plain JAR)
├── sprintly-auth         ← JWT authentication, register, login, refresh, logout
├── sprintly-user         ← User entity, profile, user listing
├── sprintly-task         ← Task CRUD, assignment, status, notifications on assign
├── sprintly-notification ← WebSocket config, notification entity, service, REST API
├── sprintly-gateway      ← Orchestrator (ONLY runnable Spring Boot app)
└── sprintly-cli          ← Terminal CLI client (Picocli + JLine REPL)
```

### Build Order
```
sprintly-common → sprintly-user → sprintly-auth → sprintly-task
    → sprintly-notification → sprintly-gateway
                            → sprintly-cli
```

---

## 🏗️ Full System Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                         CLIENT LAYER                                 │
│   Swagger UI       Postman        sprintly-cli (terminal)            │
└────────┬──────────────┬───────────────────┬─────────────────────────┘
         │              │                   │ HTTP REST calls
         │    HTTP REST + WebSocket         │ (Apache HttpClient 5)
┌────────▼──────────────▼───────────────────▼─────────────────────────┐
│                     sprintly-gateway (port 8080)                     │
│         SwaggerConfig  CorsConfig  RequestLoggingFilter              │
│         GlobalExceptionHandler                                       │
└────┬──────────────┬──────────────┬──────────────┬────────────────────┘
     │              │              │              │
┌────▼───┐  ┌───────▼───┐  ┌──────▼──────┐ ┌────▼────────────────┐
│ auth   │  │ user      │  │ task        │ │ notification        │
│        │  │           │  │             │ │                     │
│Register│  │List users │  │CRUD tasks   │ │WebSocket (STOMP)    │
│Login   │  │Find by    │  │Assign       │ │Real-time push       │
│JWT     │  │email/id   │  │Status change│ │Persist to DB        │
│Refresh │  │           │  │→ triggers   │ │Mark read/unread     │
│Logout  │  │           │  │  notif      │ │REST endpoints       │
└────────┘  └───────────┘  └─────────────┘ └─────────────────────┘
                              │
                  ┌───────────▼───────────────┐
                  │      sprintly-common       │
                  │  ApiResponse (Builder)     │
                  │  TaskFlowException hier.   │
                  │  TaskStatus / Priority     │
                  │  AppConfigManager (Single) │
                  │  NotificationFactory (Fact)│
                  └───────────┬───────────────┘
                              │
                ┌─────────────┴──────────────┐
          ┌─────▼──────┐
          │ PostgreSQL │
          │ users      │
          │ tasks      │
          │ notifs     │
          │ refresh_tk │
          └────────────┘
```

---

## 📁 Complete Folder Structure

```
sprintly/
├── pom.xml
├── docker-compose.yml
├── init.sql
├── README.md
├── CLAUDE.md                                         ← THIS FILE
│
├── sprintly-common/src/main/java/com/sprintly/common/
│   ├── dto/          ApiResponse, PagedResponse, ErrorResponse, UserDTO
│   ├── exception/    TaskFlowException, ResourceNotFoundException,
│   │                 UnauthorizedException, BadRequestException
│   ├── enums/        TaskStatus, TaskPriority
│   ├── patterns/     singleton/AppConfigManager, factory/NotificationFactory
│   └── util/         DateUtil, ValidationUtil
│
├── sprintly-auth/src/main/java/com/sprintly/auth/
│   ├── controller/   AuthController
│   ├── dto/          RegisterRequest, LoginRequest, RefreshTokenRequest, AuthResponse
│   ├── entity/       RefreshToken
│   ├── repository/   RefreshTokenRepository, JdbcRefreshTokenRepository
│   ├── security/     SecurityConfig, JwtAuthFilter
│   └── service/      AuthService, JwtService, CustomUserDetailsService
│
├── sprintly-user/src/main/java/com/sprintly/user/
│   ├── controller/   UserController            ← GET /api/users
│   ├── entity/       User
│   └── repository/   UserRepository, JdbcUserRepository
│
├── sprintly-task/src/main/java/com/sprintly/task/
│   ├── controller/   TaskController
│   ├── dto/          CreateTaskRequest, UpdateTaskRequest, TaskDTO
│   ├── entity/       Task
│   ├── mapper/       TaskMapper
│   ├── repository/   TaskRepository, JdbcTaskRepository  ← JOIN for names
│   └── service/      TaskService  ← calls NotificationService on assign
│
├── sprintly-notification/src/main/java/com/sprintly/notification/
│   ├── controller/   NotificationController    ← /api/notifications/*
│   ├── dto/          NotificationDTO
│   ├── entity/       Notification
│   ├── mapper/       NotificationMapper
│   ├── repository/   NotificationRepository, JdbcNotificationRepository
│   ├── service/      NotificationService
│   └── websocket/    WebSocketConfig, WebSocketEventListener
│
├── sprintly-gateway/src/main/java/com/sprintly/gateway/
│   ├── GatewayApplication.java                ← ONLY @SpringBootApplication
│   ├── config/       SwaggerConfig, CorsConfig
│   ├── filter/       RequestLoggingFilter
│   └── exception/    GlobalExceptionHandler
│
└── sprintly-cli/src/main/java/com/sprintly/cli/
    ├── SprintlyCli.java                        ← main() + REPL
    ├── client/
    │   └── SprintlyClient.java                 ← GET, POST, PUT + isLoggedIn()
    ├── config/
    │   └── CliConfig.java                      ← ~/.sprintly-cli.json
    ├── util/
    │   └── CliPrompt.java
    └── command/
        ├── LoginCommand.java
        ├── LogoutCommand.java
        ├── RegisterCommand.java
        ├── RefreshCommand.java
        ├── task/
        │   ├── TaskCommand.java               ← parent: sprintly task
        │   ├── CreateTaskCommand.java
        │   ├── ListTasksCommand.java
        │   └── GetTaskCommand.java
        └── notification/
            ├── NotificationCommand.java       ← parent: sprintly notification
            ├── ListNotificationsCommand.java  ← sprintly notification list
            ├── UnreadNotificationsCommand.java← sprintly notification unread
            ├── MarkReadCommand.java           ← sprintly notification read <id>
            └── MarkAllReadCommand.java        ← sprintly notification read-all
```

---

## 🖥️ CLI — Full Command Reference

### Auth Commands
```bash
sprintly register          # Create new account (saves name + tokens to config)
sprintly login             # Login (checks if already logged in first)
sprintly logout            # Logout (clears local config regardless of server)
sprintly refresh           # Refresh access token using saved refresh token
```

### Task Commands
```bash
sprintly task              # Shows task subcommand help
sprintly task list         # List all tasks (table with assignee name)
sprintly task create       # Create task (login guard → title → desc → assignee list)
sprintly task get <id>     # Get full task detail by ID
```

### Notification Commands
```bash
sprintly notification                      # Shows notification subcommand help

sprintly notification unread              # Show unread notifications + ask to mark all read
                                          # → This is the PRIMARY command users run
                                          # → Fetches GET /notifications/unread
                                          # → Displays with 🔔 badge
                                          # → Prompts "Mark all as read? [Y/n]"
                                          # → If Y: calls PUT /notifications/read-all

sprintly notification list                # Show ALL notifications (read + unread)
                                          # → read ones show ✓
                                          # → unread ones show 🔔
                                          # → Does NOT auto-mark as read

sprintly notification read <id>           # Mark single notification as read
                                          # → Calls PUT /notifications/{id}/read
                                          # → Prompts for id if not provided

sprintly notification read-all            # Mark ALL notifications as read
                                          # → Calls PUT /notifications/read-all
```

### How Notifications Flow End-to-End
```
1. User A creates a task and assigns it to User B

2. Backend (TaskService.createTask):
   → saves task to DB
   → calls NotificationService.notifyTaskAssigned()
   → NotificationService:
       a. INSERT into notifications table (read=false)
       b. WebSocket push to User B if connected

3. User B types: sprintly notification unread
   → CLI calls GET /api/notifications/unread
   → Backend returns list of unread notifications
   → CLI displays:
       🔔 #3  [UNREAD]  TASK_ASSIGNED
           Title  : Task Assigned
           Message: You have been assigned to task: Fix login bug
           Time   : 21 Mar 2026, 14:30

4. CLI asks: "Mark all as read? [Y/n]"
   → User presses Enter (defaults to Y)
   → CLI calls PUT /api/notifications/read-all
   → Backend: UPDATE notifications SET read=true WHERE recipient_id=B AND read=false
   → Notifications moved to read

5. User B types: sprintly notification list
   → Shows same notification but now with ✓ badge instead of 🔔
```

---

## 🔔 Notification Backend — API Reference

### REST Endpoints (all require JWT)
```
GET  /api/notifications              → all notifications for current user
GET  /api/notifications/unread       → unread only
GET  /api/notifications/unread/count → count of unread (for badge display)
PUT  /api/notifications/{id}/read    → mark one as read
PUT  /api/notifications/read-all     → mark all as read
DELETE /api/notifications/{id}       → delete one notification
DELETE /api/notifications            → delete all for current user
```

### Notification Types
```
TASK_ASSIGNED    → someone assigned a task to you
TASK_COMPLETED   → a task you created was completed
TASK_OVERDUE     → a task assigned to you is overdue
```

### WebSocket
```
Connect:    ws://localhost:8080/ws
Subscribe:  /user/queue/notifications       ← personal real-time alerts
Subscribe:  /topic/notifications            ← broadcast to all
Subscribe:  /user/queue/notification-count  ← unread count badge
```

---

## 🔐 Auth System

### JWT Access Token
```
Expiry    : 15 minutes
Algorithm : HS256
Claims    : { sub: email, userId, type: "access", iat, exp }
Usage     : Authorization: Bearer <token> on every API call
```

### JWT Refresh Token
```
Expiry    : 7 days
Algorithm : HS256
Stored    : PostgreSQL refresh_tokens table (revocable)
Usage     : POST /api/auth/refresh body
```

### CLI Token Storage
```
File      : ~/.sprintly-cli.json
Fields    : { accessToken, refreshToken, name, email }
Created   : on login or register
Deleted   : on logout (CliConfig.clear())

name  → saved on register (register response has the name user typed)
email → saved on login and register
```

### Logout Behaviour
```
LogoutCommand always clears ~/.sprintly-cli.json regardless of server response.

Reason: token may be expired. Server correctly rejects it.
But user still wants to be logged out of this machine.
Keeping stale token causes login/logout deadlock.

Server success → "Logged out from all devices" (refresh tokens revoked in DB)
Server failure → "Logged out locally" (local config cleared, server tokens expire naturally)
```

---

## 🧠 LLD Design Patterns

| Pattern   | Class                      | Where                     | Interview Answer                                        |
|-----------|----------------------------|---------------------------|---------------------------------------------------------|
| Singleton | `AppConfigManager`         | sprintly-common           | Double-checked locking, volatile, framework-agnostic    |
| Factory   | `NotificationFactory`      | sprintly-common           | Add new channel = 1 class + 1 switch case, zero changes elsewhere |
| Builder   | `ApiResponse`, `AuthResponse` | sprintly-common        | Immutable objects, readable construction, no telescoping constructors |
| Strategy  | `TaskStatusStrategy`       | sprintly-task (planned)   | Each transition = own class, independently testable     |
| Command   | Every Picocli @Command     | sprintly-cli              | Encapsulates action + params, follows Command pattern literally |
| Observer  | WebSocket notifications    | sprintly-notification     | TaskService (Observable) → NotificationService (Observer) → WebSocket |

---

## 🗃️ Database Schema

```sql
CREATE TABLE users (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(150) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    enabled     BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE refresh_tokens (
    id          BIGSERIAL    PRIMARY KEY,
    token       VARCHAR(500) NOT NULL UNIQUE,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at  TIMESTAMP    NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT false,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE tasks (
    id           BIGSERIAL    PRIMARY KEY,
    title        VARCHAR(200) NOT NULL,
    description  TEXT,
    status       VARCHAR(30)  NOT NULL DEFAULT 'TODO',
    created_by   BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    assigned_to  BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE notifications (
    id           BIGSERIAL    PRIMARY KEY,
    type         VARCHAR(50)  NOT NULL,
    title        VARCHAR(200) NOT NULL,
    message      TEXT,
    recipient_id BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    sender_id    BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    entity_id    BIGINT,
    entity_type  VARCHAR(50),
    read         BOOLEAN      NOT NULL DEFAULT false,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at      TIMESTAMP
);
```

---

## 🌐 Complete REST API Reference

### Auth — `/api/auth`
| Method | Path       | Auth   | Description                    |
|--------|------------|--------|--------------------------------|
| POST   | /register  | Public | Register + returns JWT tokens  |
| POST   | /login     | Public | Login + returns JWT tokens     |
| POST   | /refresh   | Public | Refresh access token           |
| POST   | /logout    | JWT    | Revoke refresh tokens          |

### Users — `/api/users`
| Method | Path  | Auth | Description       |
|--------|-------|------|-------------------|
| GET    | /     | JWT  | List all users    |

### Tasks — `/api/tasks`
| Method | Path      | Auth | Description                              |
|--------|-----------|------|------------------------------------------|
| GET    | /         | JWT  | List all tasks (with assignee names)     |
| POST   | /         | JWT  | Create task (triggers notification)      |
| GET    | /{id}     | JWT  | Get task detail                          |
| PUT    | /{id}     | JWT  | Update task                              |
| DELETE | /{id}     | JWT  | Delete task                              |

### Notifications — `/api/notifications`
| Method | Path              | Auth | Description                     |
|--------|-------------------|------|---------------------------------|
| GET    | /                 | JWT  | Get all notifications           |
| GET    | /unread           | JWT  | Get unread notifications only   |
| GET    | /unread/count     | JWT  | Get unread count                |
| PUT    | /{id}/read        | JWT  | Mark one as read                |
| PUT    | /read-all         | JWT  | Mark all as read                |
| DELETE | /{id}             | JWT  | Delete one notification         |
| DELETE | /                 | JWT  | Delete all my notifications     |

---

## 🚀 How to Run

```bash
# 1. Start PostgreSQL
docker-compose up -d

# 2. Build all modules
mvn clean install -DskipTests

# 3. Run the backend
cd sprintly-gateway && mvn spring-boot:run

# 4. Use the CLI (in any terminal)
sprintly
```

### Access Points
```
Swagger UI  → http://localhost:8080/swagger-ui.html
API Base    → http://localhost:8080/api
WebSocket   → ws://localhost:8080/ws
Config file → ~/.sprintly-cli.json
```

---

## ⚠️ Common Errors & Fixes

| Error                                    | Cause                              | Fix                                     |
|------------------------------------------|------------------------------------|-----------------------------------------|
| `Connection refused 5432`                | PostgreSQL not running             | `docker-compose up -d`                  |
| `JWT secret too short`                   | Secret < 32 chars                  | Update `jwt.secret` in application.yml  |
| `Table does not exist`                   | DB not initialized                 | Run `init.sql` against your DB          |
| `MismatchedInputException end-of-input`  | Empty body from server             | Fixed in SprintlyClient.parseResponse() |
| `Logout failed: Please login first`      | Expired token in config file       | `rm ~/.sprintly-cli.json` then login    |
| `logged in as: null`                     | Old config has no name field       | Fixed: resolveDisplayName() fallback    |
| `notification subcommand not found`      | NotificationCommand not registered | Add to SprintlyCli subcommands list     |

---

## 📋 Development Phases

| Phase | Module                  | Status           | Key Deliverables                                  |
|-------|-------------------------|------------------|---------------------------------------------------|
| 1     | `sprintly-common`       | ✅ Complete      | DTOs, exceptions, enums, Singleton, Factory       |
| 2     | `sprintly-auth`         | ✅ Complete      | JWT, SecurityConfig, JwtAuthFilter                |
| 3     | `sprintly-user`         | ✅ Complete      | User entity, JDBC repo, list endpoint             |
| 4     | `sprintly-task`         | ✅ Complete      | Task CRUD, assignee, notification trigger         |
| 5     | `sprintly-notification` | ✅ Complete      | WebSocket, JDBC repo, all REST endpoints          |
| 6     | `sprintly-gateway`      | ✅ Complete      | Swagger, CORS, filters, GlobalExceptionHandler    |
| 7     | `sprintly-cli`          | ✅ Complete      | Auth + Task + Notification commands, REPL, guards |

---

## 🎤 Interview Talking Points

### Architecture
- "Multi-module Maven — clean separation like microservices but a single deployable JAR. Only the gateway has `@SpringBootApplication`."
- "Used raw JDBC (JdbcTemplate) instead of JPA — full control over SQL, explicit JOIN queries for performance."

### Notification System
- "When a task is assigned, `TaskService` calls `NotificationService.notifyTaskAssigned()`. This persists a notification to DB and pushes it via WebSocket using `SimpMessagingTemplate.convertAndSendToUser()`."
- "The Factory pattern in `NotificationFactory` means adding EMAIL or PUSH notifications requires zero changes to `NotificationService`."
- "The CLI's `notification unread` command fetches unread, displays them, then asks the user to acknowledge — same UX as a mobile notification inbox."

### CLI Design
- "The CLI is a completely separate Maven module. It calls the same REST API as Swagger UI — no special backend changes needed."
- "`SprintlyClient.parseResponse()` reads the HTTP body as a String first before passing to Jackson. This fixes `MismatchedInputException` when the server returns an empty body on 401/403."
- "`LogoutCommand` always clears the local config regardless of server response — prevents the logout deadlock caused by expired tokens."
- "The REPL mode (JLine) gives an interactive shell experience. Single commands also work directly: `sprintly task list`."

### Security
- "Access tokens: stateless JWT, 15-min expiry. Refresh tokens: stored in DB, revocable instantly."
- "Config saved at `~/.sprintly-cli.json`. In production we'd use OS keychain or encrypt the file."