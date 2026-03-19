# 🚀 CLAUDE.md — Sprintly Project Master Reference

> This file is the single source of truth for the entire Sprintly project.
> Read this before making any changes to any module.

---

## 📌 Project Identity

| Field        | Value                                                                 |
|--------------|-----------------------------------------------------------------------|
| Name         | **Sprintly**                                                          |
| Description  | ⚡ Sprintly — Real-time collaborative task management backend. Built with Spring Boot 3, JWT Auth, OAuth2, WebSocket, multi-module Maven architecture and clean LLD design patterns. |
| Java Version | 17 (LTS)                                                              |
| Spring Boot  | 3.2.4                                                                 |
| Build Tool   | Maven Multi-module                                                    |
| Entry Point  | `sprintly-gateway` — the ONLY runnable `@SpringBootApplication`       |
| GitHub Repo  | `sprintly`                                                            |

---

## 🗂️ Module Map

```
sprintly/                              ← Root (parent pom.xml)
├── sprintly-common                    ← Shared library (plain JAR, no main)
├── sprintly-auth                      ← JWT + OAuth2 authentication
├── sprintly-user                      ← User profiles + role management
├── sprintly-task                      ← Core task management
├── sprintly-notification              ← WebSocket real-time notifications
├── sprintly-gateway                   ← Orchestrator (ONLY runnable app)
└── sprintly-cli                       ← CLI client (Picocli + Lanterna TUI)
```

### Build Order (Maven dependency chain)
```
sprintly-common
    → sprintly-user
        → sprintly-auth (depends on sprintly-user for User entity)
            → sprintly-task
                → sprintly-notification
                    → sprintly-gateway (imports ALL modules)
                    → sprintly-cli     (standalone, calls REST API)
```

---

## 🏗️ Full System Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                         CLIENT LAYER                                 │
│                                                                      │
│   Swagger UI      Postman      Web Browser      sprintly-cli         │
│   (testing)       (testing)    (future UI)      (terminal)           │
└────────┬─────────────┬───────────────┬───────────────┬──────────────┘
         │             │               │               │
         │      HTTP REST + WebSocket  │         HTTP REST calls
         │                             │         (Java HttpClient)
┌────────▼─────────────────────────────▼──────────────────────────────┐
│                       sprintly-gateway                               │
│              (Orchestrator — ONLY @SpringBootApplication)            │
│                                                                      │
│  SwaggerConfig    CorsConfig    RedisConfig    RequestLoggingFilter  │
│  GlobalExceptionHandler                                              │
│                                                                      │
│  scanBasePackages = "com.sprintly"  ← picks up ALL module beans      │
└──────┬──────────────┬──────────────┬──────────────┬─────────────────┘
       │              │              │              │
┌──────▼──────┐ ┌─────▼──────┐ ┌────▼──────┐ ┌────▼────────────────┐
│sprintly-    │ │sprintly-   │ │sprintly-  │ │sprintly-            │
│auth         │ │user        │ │task       │ │notification         │
│             │ │            │ │           │ │                     │
│• Register   │ │• Profile   │ │• CRUD     │ │• WebSocket (STOMP)  │
│• Login      │ │• Roles     │ │• Assign   │ │• Real-time alerts   │
│• JWT issue  │ │• Search    │ │• Status   │ │• Task assigned notif│
│• Refresh    │ │• List devs │ │• Comments │ │• In-app + Email     │
│• Logout     │ │  for assign│ │• Strategy │ │• NotificationFactory│
│• OAuth2     │ │            │ │  pattern  │ │                     │
└──────┬──────┘ └─────┬──────┘ └────┬──────┘ └────┬────────────────┘
       │              │              │              │
       └──────────────┴──────────────┴──────────────┘
                              │
                  ┌───────────▼───────────────┐
                  │      sprintly-common       │
                  │                           │
                  │  ApiResponse (Builder)     │
                  │  PagedResponse (Builder)   │
                  │  ErrorResponse (Builder)   │
                  │  TaskFlowException         │
                  │  ResourceNotFoundException │
                  │  UnauthorizedException     │
                  │  BadRequestException       │
                  │  TaskStatus enum           │
                  │  TaskPriority enum         │
                  │  UserRole enum             │
                  │  AppConfigManager(Singlet.)│
                  │  NotificationFactory(Fact.)│
                  │  DateUtil, ValidationUtil  │
                  └───────────┬───────────────┘
                              │
                ┌─────────────┴──────────────┐
          ┌─────▼──────┐              ┌───────▼─────┐
          │ PostgreSQL │              │    Redis     │
          │            │              │              │
          │ users      │              │ JWT blacklist│
          │ tasks      │              │ token cache  │
          │ comments   │              │              │
          │ notifs     │              └─────────────-┘
          │ refresh_tk │
          └────────────┘
```

---

## 📁 Complete Folder Structure

```
sprintly/
├── pom.xml                                        ← Parent POM
├── docker-compose.yml                             ← PostgreSQL + Redis
├── README.md
├── CLAUDE.md                                      ← THIS FILE
│
├── sprintly-common/
│   ├── pom.xml
│   └── src/main/java/com/sprintly/common/
│       ├── dto/
│       │   ├── ApiResponse.java                   ← Builder pattern
│       │   ├── PagedResponse.java                 ← Builder pattern
│       │   └── ErrorResponse.java                 ← Builder pattern
│       ├── exception/
│       │   ├── TaskFlowException.java             ← Base exception
│       │   ├── ResourceNotFoundException.java     ← 404
│       │   ├── UnauthorizedException.java         ← 401
│       │   └── BadRequestException.java           ← 400
│       ├── enums/
│       │   ├── TaskStatus.java                    ← TODO→IN_PROGRESS→IN_REVIEW→DONE
│       │   ├── TaskPriority.java                  ← LOW/MEDIUM/HIGH/CRITICAL
│       │   └── UserRole.java                      ← ROLE_ADMIN/MANAGER/DEVELOPER
│       ├── patterns/
│       │   ├── singleton/
│       │   │   └── AppConfigManager.java          ← Singleton (double-checked locking)
│       │   └── factory/
│       │       ├── Notification.java              ← Factory interface
│       │       ├── NotificationFactory.java        ← Factory implementation
│       │       └── NotificationType.java           ← IN_APP / EMAIL / PUSH
│       └── util/
│           ├── DateUtil.java
│           └── ValidationUtil.java
│
├── sprintly-auth/
│   ├── pom.xml
│   └── src/main/java/com/sprintly/auth/
│       ├── controller/
│       │   └── AuthController.java                ← /api/auth/*
│       ├── dto/
│       │   ├── RegisterRequest.java
│       │   ├── LoginRequest.java
│       │   ├── RefreshTokenRequest.java
│       │   └── AuthResponse.java
│       ├── entity/
│       │   └── RefreshToken.java
│       ├── repository/
│       │   └── RefreshTokenRepository.java
│       ├── security/
│       │   ├── SecurityConfig.java
│       │   ├── JwtAuthFilter.java
│       │   └── OAuth2SuccessHandler.java
│       └── service/
│           ├── AuthService.java
│           ├── JwtService.java
│           └── CustomUserDetailsService.java
│
├── sprintly-user/
│   ├── pom.xml
│   └── src/main/java/com/sprintly/user/
│       ├── controller/
│       │   └── UserController.java                ← /api/users/*
│       ├── dto/
│       │   ├── UserDTO.java
│       │   └── UpdateUserRequest.java
│       ├── entity/
│       │   └── User.java
│       ├── mapper/
│       │   └── UserMapper.java                    ← MapStruct
│       ├── repository/
│       │   └── UserRepository.java
│       └── service/
│           └── UserService.java
│
├── sprintly-task/
│   ├── pom.xml
│   └── src/main/java/com/sprintly/task/
│       ├── controller/
│       │   ├── TaskController.java                ← /api/tasks/*
│       │   └── CommentController.java             ← /api/tasks/{id}/comments
│       ├── dto/
│       │   ├── TaskDTO.java
│       │   ├── CreateTaskRequest.java             ← includes assigneeId field
│       │   ├── UpdateTaskRequest.java
│       │   └── CommentDTO.java
│       ├── entity/
│       │   ├── Task.java
│       │   └── Comment.java
│       ├── mapper/
│       │   └── TaskMapper.java
│       ├── repository/
│       │   ├── TaskRepository.java
│       │   └── CommentRepository.java
│       ├── service/
│       │   ├── TaskService.java                   ← triggers notification on assign
│       │   └── CommentService.java
│       └── strategy/
│           ├── TaskStatusStrategy.java            ← Strategy interface
│           ├── TodoToInProgressStrategy.java
│           ├── InProgressToReviewStrategy.java
│           ├── ReviewToDoneStrategy.java
│           ├── ReviewToInProgressStrategy.java
│           ├── AnyCancelledStrategy.java
│           └── TaskStatusStrategyFactory.java
│
├── sprintly-notification/
│   ├── pom.xml
│   └── src/main/java/com/sprintly/notification/
│       ├── controller/
│       │   └── NotificationController.java        ← /api/notifications/*
│       ├── dto/
│       │   └── NotificationDTO.java
│       ├── entity/
│       │   └── Notification.java
│       ├── repository/
│       │   └── NotificationRepository.java
│       ├── service/
│       │   └── NotificationService.java           ← sends via Factory + WebSocket
│       └── websocket/
│           ├── WebSocketConfig.java
│           └── WebSocketEventListener.java
│
├── sprintly-gateway/
│   ├── pom.xml
│   └── src/main/java/com/sprintly/gateway/
│       ├── GatewayApplication.java                ← ONLY main() in project
│       ├── config/
│       │   ├── SwaggerConfig.java
│       │   ├── CorsConfig.java
│       │   └── RedisConfig.java
│       ├── filter/
│       │   └── RequestLoggingFilter.java
│       └── exception/
│           └── GlobalExceptionHandler.java
│
└── sprintly-cli/
    ├── pom.xml
    └── src/main/java/com/sprintly/cli/
        ├── SprintlyCliApplication.java
        ├── commands/
        │   ├── RootCommand.java
        │   ├── LoginCommand.java
        │   ├── LogoutCommand.java
        │   ├── TaskCommand.java
        │   ├── task/
        │   │   ├── TaskListCommand.java
        │   │   ├── TaskCreateCommand.java        ← fetches user list, lets user select
        │   │   ├── TaskUpdateCommand.java
        │   │   └── TaskDeleteCommand.java
        │   ├── SyncCommand.java
        │   └── DashboardCommand.java             ← launches TUI
        ├── tui/
        │   ├── TuiApplication.java
        │   ├── screens/
        │   │   ├── DashboardScreen.java          ← Kanban board
        │   │   ├── TaskDetailScreen.java
        │   │   ├── CreateTaskScreen.java         ← dropdown user selector
        │   │   └── LoginScreen.java
        │   └── components/
        │       ├── TaskTable.java
        │       ├── UserSelector.java             ← reusable assignee dropdown
        │       ├── StatusBar.java
        │       └── NotificationPanel.java
        ├── client/
        │   ├── SprintlyApiClient.java            ← Singleton HTTP client
        │   ├── AuthApiClient.java
        │   ├── TaskApiClient.java
        │   └── UserApiClient.java
        ├── offline/
        │   ├── OfflineStorage.java               ← SQLite via JDBC
        │   ├── OfflineTaskRepository.java
        │   ├── SyncEngine.java
        │   └── ConflictResolver.java
        ├── config/
        │   ├── CliConfig.java                    ← ~/.sprintly/config.yml
        │   └── TokenStore.java                   ← ~/.sprintly/token
        ├── output/
        │   ├── TablePrinter.java
        │   ├── ColorPrinter.java
        │   └── JsonPrinter.java
        └── patterns/
            ├── singleton/
            │   └── HttpClientManager.java        ← Singleton HTTP client
            └── factory/
                └── OutputFormatterFactory.java   ← table/json/plain output
```

---

## 🔔 Notification System — Task Assignment Flow

This is a core feature. When ANY role (ADMIN/MANAGER/DEVELOPER) creates a task
and assigns it to a developer, the assignee must receive a real-time notification.

### Assignment Rules
```
Who can assign tasks?
  ADMIN     → can assign to anyone
  MANAGER   → can assign to any DEVELOPER in the system
  DEVELOPER → can assign to themselves or any other DEVELOPER

Where does the assignee list come from?
  GET /api/users?role=ROLE_DEVELOPER
  → returns list of all developers
  → used by frontend dropdown AND CLI selector
```

### Task Creation + Notification Flow
```
POST /api/tasks
{
  "title":      "Fix login redirect",
  "description": "...",
  "priority":   "HIGH",
  "dueDate":    "2025-04-01",
  "assigneeId": 5              ← selected from developer list
}

                    │
                    ▼
          TaskController.createTask()
                    │
                    ▼
          TaskService.createTask()
            │
            ├── 1. Validate assigneeId exists and is ROLE_DEVELOPER
            │         UserRepository.findById(assigneeId)
            │         if role != DEVELOPER → throw BadRequestException
            │
            ├── 2. Save task to PostgreSQL
            │         task.setAssignee(assigneeUser)
            │         taskRepository.save(task)
            │
            └── 3. Trigger notification (if assignee != creator)
                      notificationService.notifyTaskAssigned(task, assignee)
                                │
                                ▼
                      ┌─────────────────────────────┐
                      │   NotificationService        │
                      │                             │
                      │ a) Persist to DB:            │
                      │    INSERT into notifications │
                      │    { userId: assignee.id,   │
                      │      type: TASK_ASSIGNED,   │
                      │      message: "You have     │
                      │      been assigned TSK-42:  │
                      │      Fix login redirect",   │
                      │      read: false }           │
                      │                             │
                      │ b) Factory creates notif:   │
                      │    NotificationFactory      │
                      │      .create(IN_APP)        │
                      │      .send(assigneeId, ...) │
                      │                             │
                      │ c) WebSocket push:          │
                      │    messagingTemplate        │
                      │    .convertAndSendToUser(   │
                      │      assignee.email,        │
                      │      "/queue/notifications",│
                      │      notificationDTO)       │
                      └─────────────────────────────┘
                                │
                                ▼
                      Assignee's connected client
                      receives real-time WebSocket message:
                      {
                        "type": "TASK_ASSIGNED",
                        "taskId": 42,
                        "taskTitle": "Fix login redirect",
                        "assignedBy": "Priya (Manager)",
                        "priority": "HIGH",
                        "timestamp": "..."
                      }
```

### Notification Entity (DB)
```sql
CREATE TABLE notifications (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users(id),
    type         VARCHAR(50) NOT NULL,   -- TASK_ASSIGNED, TASK_UPDATED, etc.
    title        VARCHAR(200) NOT NULL,
    message      TEXT        NOT NULL,
    task_id      BIGINT      REFERENCES tasks(id),
    is_read      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_user_unread ON notifications(user_id, is_read);
```

### Notification API Endpoints
```
GET  /api/notifications              → get my notifications (paginated)
GET  /api/notifications/unread-count → get unread count badge
PATCH /api/notifications/{id}/read  → mark one as read
PATCH /api/notifications/read-all   → mark all as read

WebSocket:
  Subscribe: /user/queue/notifications  → receive real-time alerts
  Subscribe: /topic/tasks               → team-wide task updates
```

---

## 👥 User Listing for Task Assignment

### Backend Endpoint
```
GET /api/users?role=ROLE_DEVELOPER

Response:
{
  "success": true,
  "data": [
    { "id": 3, "name": "Ravi Kumar",  "email": "ravi@sprintly.com",  "role": "ROLE_DEVELOPER" },
    { "id": 5, "name": "Priya Singh", "email": "priya@sprintly.com", "role": "ROLE_DEVELOPER" },
    { "id": 7, "name": "Arjun Mehta","email": "arjun@sprintly.com", "role": "ROLE_DEVELOPER" }
  ]
}
```

### In the REST API (Swagger / Postman)
```
Step 1: GET /api/users?role=ROLE_DEVELOPER   → see the list
Step 2: Copy the id of the developer you want
Step 3: POST /api/tasks { ..., "assigneeId": 5 }
```

### In the CLI (sprintly task create)
```
$ sprintly task create

Title: Fix login redirect bug
Description: The OAuth2 redirect is not working on Safari
Priority (LOW/MEDIUM/HIGH/CRITICAL): HIGH
Due Date (YYYY-MM-DD): 2025-04-01

Fetching available developers...

Select Assignee:
  ❯ 1. Ravi Kumar      (ravi@sprintly.com)
    2. Priya Singh     (priya@sprintly.com)
    3. Arjun Mehta     (arjun@sprintly.com)
    4. Unassigned

[Use arrow keys to navigate, Enter to select]

✅ Task created: TSK-42 — Fix login redirect bug
✅ Assigned to: Priya Singh
✅ Priya will be notified in real-time
```

The CLI fetches the list via `GET /api/users?role=ROLE_DEVELOPER` before showing
the selection menu. The user never types an ID — they pick from an interactive list.

### In the TUI Dashboard (sprintly dashboard → N for New Task)
```
┌──────────────── New Task ─────────────────────┐
│                                               │
│  Title:    [Fix login redirect bug_________]  │
│  Priority: [HIGH ▼]                           │
│  Due Date: [2025-04-01__]                     │
│                                               │
│  Assign To:                                   │
│  ┌─────────────────────────────────────────┐  │
│  │ ❯ Ravi Kumar      (ravi@sprintly.com)   │  │
│  │   Priya Singh     (priya@sprintly.com)  │  │
│  │   Arjun Mehta     (arjun@sprintly.com)  │  │
│  │   Unassigned                            │  │
│  └─────────────────────────────────────────┘  │
│                                               │
│      [Create Task]        [Cancel]            │
└───────────────────────────────────────────────┘
```

---

## 🔐 Security Model

```
Endpoint                          Public  Auth  DEVELOPER  MANAGER  ADMIN
──────────────────────────────────────────────────────────────────────────
POST /api/auth/**                   ✓
GET  /swagger-ui/**                 ✓
GET  /actuator/health               ✓
GET  /oauth2/**                     ✓
──────────────────────────────────────────────────────────────────────────
GET  /api/users                            ✓        ✓         ✓       ✓
GET  /api/users?role=ROLE_DEVELOPER        ✓        ✓         ✓       ✓  ← for assign list
PUT  /api/users/{id} (own profile)         ✓        ✓         ✓       ✓
DELETE /api/users/{id}                                                  ✓
──────────────────────────────────────────────────────────────────────────
GET  /api/tasks                            ✓        ✓         ✓       ✓
POST /api/tasks                                     ✓         ✓       ✓
PATCH /api/tasks/{id}/status               ✓        ✓         ✓       ✓
DELETE /api/tasks/{id}                                         ✓       ✓
POST /api/tasks/{id}/assign                                    ✓       ✓
──────────────────────────────────────────────────────────────────────────
GET  /api/notifications                    ✓        ✓         ✓       ✓
PATCH /api/notifications/{id}/read         ✓        ✓         ✓       ✓
──────────────────────────────────────────────────────────────────────────
WS /ws/                                    ✓        ✓         ✓       ✓
```

---

## 🔑 Auth System

### JWT Access Token
```
Algorithm : HS256
Expiry    : 15 minutes
Claims    : { sub: email, userId, role, type: "access", iat, exp }
Usage     : Authorization: Bearer <token>  on every API call
Stored    : JavaScript memory only (NOT localStorage)
```

### JWT Refresh Token
```
Algorithm : HS256
Expiry    : 7 days
Claims    : { sub: email, type: "refresh", iat, exp }
Usage     : POST /api/auth/refresh body
Stored    : httpOnly cookie (invisible to JavaScript)
            + persisted in refresh_tokens table (revocable)
```

### Token Rotation
```
Every /refresh call:
  1. Validate old refresh token (DB check: exists + not revoked + not expired)
  2. Mark old token revoked = true
  3. Issue new access token
  4. Issue new refresh token
  5. Save new refresh token to DB
```

### OAuth2 Google Flow
```
1. Browser → GET /oauth2/authorization/google
2. Spring Security redirects → Google login page
3. Ravi enters Google credentials (TaskFlow NEVER sees this)
4. Google shows consent screen → Ravi approves
5. Google redirects → /login/oauth2/code/google?code=xyz
6. Spring Security exchanges code for Google ID token (server-to-server)
7. OAuth2SuccessHandler fires:
   a. Extract email, name, sub from Google ID token
   b. Find-or-create User in DB (password = NULL for OAuth2 users)
   c. Issue OUR OWN JWT tokens (Google tokens discarded)
   d. Redirect to frontend with accessToken + refreshToken
8. From here: works exactly like email/password login
```

### Password Storage for OAuth2 Users
```
OAuth2 users:     password = NULL in DB
                  Validated by Google's signature on the ID token
                  TaskFlow trusts Google's verification

Email/password:   password = BCrypt(strength=12) hash
                  Never stored in plaintext. Ever.
```

---

## 🧠 LLD Design Patterns

### 1. Singleton — `AppConfigManager`
```java
// Thread-safe lazy initialization via double-checked locking
public static AppConfigManager getInstance() {
    if (instance == null) {
        synchronized (AppConfigManager.class) {
            if (instance == null) {
                instance = new AppConfigManager();
            }
        }
    }
    return instance;
}
// volatile on instance field prevents instruction reordering
```
**Interview answer:** "Spring @Component is also singleton but Spring-managed.
This works outside Spring context — in static utilities, CLI code, early startup."

---

### 2. Factory — `NotificationFactory`
```java
// In NotificationService.notifyTaskAssigned():
Notification notification = NotificationFactory.create(NotificationType.IN_APP);
notification.send(assignee.getId(), "Task Assigned", "You have been assigned: " + task.getTitle());

// Adding new type (e.g. SMS): just add enum value + class + switch case
// Zero changes to existing code → Open/Closed Principle
```
**Interview answer:** "Caller doesn't know or care if it's IN_APP, EMAIL, or PUSH.
Adding a new channel requires zero changes to NotificationService."

---

### 3. Builder — `ApiResponse`, `AuthResponse`, `ErrorResponse`
```java
return ApiResponse.<TaskDTO>builder()
    .success(true)
    .message("Task created and assignee notified")
    .data(taskDto)
    .build();
```
**Interview answer:** "Produces immutable objects. Much more readable than
a 7-parameter constructor. @Builder.Default handles timestamp auto-fill."

---

### 4. Strategy — `TaskStatusStrategy`
```
Legal transitions:
  TODO         → IN_PROGRESS
  IN_PROGRESS  → IN_REVIEW
  IN_REVIEW    → DONE
  IN_REVIEW    → IN_PROGRESS  (sent back for fixes)
  ANY          → CANCELLED

Each transition = one Strategy class with validate() + execute()
Factory picks the right strategy from (currentStatus, newStatus)
```
**Interview answer:** "Instead of a switch with 10 cases, each transition is
its own class — independently testable, independently deployable."

---

### 5. Command Pattern — `sprintly-cli`
```
Every Picocli @Command class IS the Command pattern:
  LoginCommand    → encapsulates login action + parameters
  TaskListCommand → encapsulates list action + filters
  SyncCommand     → encapsulates sync logic

Can be queued, logged, undone (future feature)
```

---

### 6. Observer — WebSocket Notifications
```
Task created/assigned → TaskService (Observable)
                     → NotificationService.notify() (Observer)
                     → WebSocket push to subscribed clients
                     → TUI NotificationPanel updates in real-time
```

---

## 🖥️ CLI Module — sprintly-cli

### Tech Stack
```
Picocli 4.x      → command parsing + auto --help
Lanterna 3.x     → interactive TUI (terminal UI, arrow keys, forms)
Java HttpClient  → calls Sprintly REST API
Jackson          → JSON serialization
SQLite JDBC      → offline local storage (~/.sprintly/local.db)
SnakeYAML        → config file (~/.sprintly/config.yml)
GraalVM          → package as native binary (no JVM needed to run)
```

### Command Reference
```bash
# Auth
sprintly login                          # prompts email + password
sprintly logout

# Tasks
sprintly task list                      # table output
sprintly task list --status TODO        # filtered
sprintly task list --json               # JSON for scripting/CI/CD
sprintly task create                    # interactive prompts + assignee selector
sprintly task update TSK-42 --status IN_PROGRESS
sprintly task delete TSK-42

# Offline
sprintly task create --offline          # save locally, sync later
sprintly sync                           # push offline changes to server

# Interactive TUI
sprintly dashboard                      # full Kanban board, arrow-key navigation
```

### Assignee Selection in CLI
```
TaskCreateCommand calls:
  1. GET /api/users?role=ROLE_DEVELOPER  → fetch developer list
  2. Render interactive list (Picocli + ANSI colors)
  3. User presses arrow keys to select
  4. Selected userId goes into CreateTaskRequest.assigneeId
  5. POST /api/tasks with assigneeId
  6. Server creates task + sends WebSocket notification to assignee
```

### Offline Sync Flow
```
OFFLINE → tasks saved to SQLite with status = PENDING_SYNC
ONLINE  → sprintly sync:
           a. Read all PENDING_SYNC records from SQLite
           b. POST each to /api/tasks
           c. If conflict (server version newer) → ask user: [L]ocal/[S]erver/[M]erge
           d. Mark synced records as SYNCED in SQLite
```

### Token Storage (CLI)
```
~/.sprintly/
├── config.yml          ← server URL, preferences
├── token               ← JWT access + refresh tokens (file permissions: 600)
└── local.db            ← SQLite offline storage
```

---

## 🗃️ Database Schema

```sql
-- ── Users ──────────────────────────────────────────────────────
CREATE TABLE users (
    id                  BIGSERIAL    PRIMARY KEY,
    name                VARCHAR(100) NOT NULL,
    email               VARCHAR(150) NOT NULL UNIQUE,
    password            VARCHAR(255),            -- NULL for OAuth2 users
    role                VARCHAR(20)  NOT NULL DEFAULT 'ROLE_DEVELOPER',
    oauth2_provider     VARCHAR(30),
    oauth2_provider_id  VARCHAR(100),
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP
);

-- ── Refresh Tokens ──────────────────────────────────────────────
CREATE TABLE refresh_tokens (
    id          BIGSERIAL    PRIMARY KEY,
    token       VARCHAR(500) NOT NULL UNIQUE,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at  TIMESTAMP    NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ── Tasks ───────────────────────────────────────────────────────
CREATE TABLE tasks (
    id           BIGSERIAL    PRIMARY KEY,
    title        VARCHAR(200) NOT NULL,
    description  TEXT,
    status       VARCHAR(20)  NOT NULL DEFAULT 'TODO',
    priority     VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM',
    assignee_id  BIGINT       REFERENCES users(id),     -- nullable = unassigned
    created_by   BIGINT       NOT NULL REFERENCES users(id),
    due_date     TIMESTAMP,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP
);

-- ── Comments ────────────────────────────────────────────────────
CREATE TABLE comments (
    id         BIGSERIAL PRIMARY KEY,
    task_id    BIGINT    NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    author_id  BIGINT    NOT NULL REFERENCES users(id),
    body       TEXT      NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- ── Notifications ────────────────────────────────────────────────
CREATE TABLE notifications (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type       VARCHAR(50)  NOT NULL,    -- TASK_ASSIGNED, TASK_UPDATED, COMMENT_ADDED
    title      VARCHAR(200) NOT NULL,
    message    TEXT         NOT NULL,
    task_id    BIGINT       REFERENCES tasks(id),
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ── Indexes ─────────────────────────────────────────────────────
CREATE INDEX idx_tasks_assignee      ON tasks(assignee_id);
CREATE INDEX idx_tasks_created_by    ON tasks(created_by);
CREATE INDEX idx_tasks_status        ON tasks(status);
CREATE INDEX idx_notifs_user         ON notifications(user_id);
CREATE INDEX idx_notifs_user_unread  ON notifications(user_id, is_read);
CREATE INDEX idx_refresh_user        ON refresh_tokens(user_id);
```

---

## 🌐 Complete REST API Reference

### Auth — `/api/auth`
| Method | Path             | Auth    | Body                            | Response         |
|--------|------------------|---------|--------------------------------|------------------|
| POST   | /register        | Public  | `{name, email, password}`       | AuthResponse     |
| POST   | /login           | Public  | `{email, password}`             | AuthResponse     |
| POST   | /refresh         | Public  | `{refreshToken}`                | AuthResponse     |
| POST   | /logout          | JWT     | —                               | 200 OK           |
| GET    | /oauth2/info     | Public  | —                               | OAuth2 URL info  |

### Users — `/api/users`
| Method | Path             | Auth    | Notes                                          |
|--------|------------------|---------|------------------------------------------------|
| GET    | /                | JWT     | List all users. `?role=ROLE_DEVELOPER` filter  |
| GET    | /{id}            | JWT     | Get user by ID                                 |
| PUT    | /{id}            | JWT     | Update own profile                             |
| DELETE | /{id}            | ADMIN   | Delete user                                    |

### Tasks — `/api/tasks`
| Method | Path                      | Auth      | Notes                                    |
|--------|---------------------------|-----------|------------------------------------------|
| GET    | /                         | JWT       | List tasks. Filterable by status/priority|
| POST   | /                         | DEV+      | Create task. `assigneeId` in body        |
| GET    | /{id}                     | JWT       | Get task detail                          |
| PUT    | /{id}                     | DEV+      | Update task fields                       |
| PATCH  | /{id}/status              | DEV+      | Change status (Strategy pattern)         |
| DELETE | /{id}                     | MANAGER+  | Delete task                              |
| POST   | /{id}/assign/{userId}     | MANAGER+  | Assign task → triggers notification      |
| GET    | /{id}/comments            | JWT       | Get comments                             |
| POST   | /{id}/comments            | JWT       | Add comment                              |

### Notifications — `/api/notifications`
| Method | Path                      | Auth  | Notes                          |
|--------|---------------------------|-------|--------------------------------|
| GET    | /                         | JWT   | Get my notifications (paged)   |
| GET    | /unread-count             | JWT   | Get unread badge count         |
| PATCH  | /{id}/read                | JWT   | Mark one notification as read  |
| PATCH  | /read-all                 | JWT   | Mark all as read               |

### WebSocket — `ws://localhost:8080/ws`
| Type      | Destination                    | Description                       |
|-----------|--------------------------------|-----------------------------------|
| Subscribe | /user/queue/notifications      | Personal real-time notifications  |
| Subscribe | /topic/tasks                   | Team-wide task board updates      |
| Send      | /app/task.update               | Broadcast a task change           |

---

## 🚀 How to Run

### Step 1 — Start Infrastructure
```bash
# From project root (where docker-compose.yml is)
docker-compose up -d

# Verify:
# PostgreSQL running on localhost:5432
# Redis     running on localhost:6379
```

### Step 2 — Build All Modules
```bash
# From project root (where parent pom.xml is)
mvn clean install
```

### Step 3 — Run the Application
```bash
cd sprintly-gateway
mvn spring-boot:run
```

### Step 4 — Access Points
```
Swagger UI  → http://localhost:8080/swagger-ui.html
API Base    → http://localhost:8080/api
WebSocket   → ws://localhost:8080/ws
Health      → http://localhost:8080/actuator/health
```

### Step 5 — Test the Notification Flow
```bash
# 1. Register two users
POST /api/auth/register  { "name": "Priya", "email": "priya@test.com", "password": "Pass@1234" }
POST /api/auth/register  { "name": "Ravi",  "email": "ravi@test.com",  "password": "Pass@1234" }

# 2. Login as Priya (manager), get her token
POST /api/auth/login  { "email": "priya@test.com", "password": "Pass@1234" }

# 3. Get developer list (to find Ravi's ID)
GET /api/users?role=ROLE_DEVELOPER   [Priya's token]

# 4. Create task assigned to Ravi
POST /api/tasks  [Priya's token]
{ "title": "Fix login redirect", "priority": "HIGH", "assigneeId": <ravi's id> }

# 5. Ravi receives WebSocket notification instantly
# Subscribe to ws://localhost:8080/ws → /user/queue/notifications
```

---

## ⚠️ Common Errors & Fixes

| Error                              | Cause                           | Fix                                    |
|------------------------------------|---------------------------------|----------------------------------------|
| `Connection refused 5432`          | PostgreSQL not running          | `docker-compose up -d`                 |
| `Connection refused 6379`          | Redis not running               | `docker-compose up -d`                 |
| `JWT secret too short`             | Secret < 32 characters          | Update `jwt.secret` in application.yml |
| `Table 'users' doesn't exist`      | DDL not applied                 | Set `ddl-auto: update`                 |
| `Circular dependency`              | Bean injection issue            | Check SecurityConfig constructor       |
| `Assignee not found`               | Invalid assigneeId in request   | GET /api/users first to get valid IDs  |
| `Cannot assign to non-developer`   | assigneeId has wrong role       | Only ROLE_DEVELOPER users can be assigned |
| `WebSocket 403`                    | Missing JWT in WS handshake     | Send token in WS connect headers       |

---

## 📋 Development Phases

| Phase | Module                  | Status           | Key Deliverables                               |
|-------|-------------------------|------------------|------------------------------------------------|
| 1     | `sprintly-common`       | ✅ Complete      | DTOs, exceptions, enums, Singleton, Factory    |
| 2     | `sprintly-auth`         | ✅ Complete      | JWT, OAuth2, SecurityConfig, JwtAuthFilter     |
| 3     | `sprintly-user`         | 🔲 Next          | User CRUD, role filter, developer list API     |
| 4     | `sprintly-task`         | 🔲 Planned       | Task CRUD, assigneeId, Strategy pattern        |
| 5     | `sprintly-notification` | 🔲 Planned       | WebSocket, task-assigned notification flow     |
| 6     | `sprintly-gateway`      | ✅ Scaffold done | Swagger, CORS, filters, GlobalExceptionHandler |
| 7     | `sprintly-cli`          | 🔲 Planned       | Commands, TUI, offline mode, assignee selector |

---

## 🎤 Interview Talking Points

### Architecture
- "Multi-module Maven — like microservices but in one deployable JAR. Separation of concerns without the operational overhead."
- "Only the gateway has @SpringBootApplication. All other modules are plain JARs the gateway imports. Spring's component scan picks up all beans automatically."

### Notification System
- "When a task is assigned, TaskService calls NotificationService which uses the Factory pattern to create the right notification type (IN_APP/EMAIL/PUSH), persists it to the DB, and pushes it via WebSocket to the assignee's subscribed channel."
- "The assignee list for task creation is fetched from GET /api/users?role=ROLE_DEVELOPER — same endpoint used by both REST clients and the CLI selector."

### Security
- "Access tokens are stateless JWTs verified by signature alone — zero DB calls. Refresh tokens are stateful, stored in PostgreSQL, and can be instantly revoked."
- "OAuth2 users have password = NULL. We trust Google's signature on the ID token rather than storing or validating a password ourselves."

### LLD Patterns
- "Singleton for AppConfigManager — double-checked locking, volatile, framework-agnostic."
- "Factory for notifications — add a new channel by adding one class + one switch case. Zero changes to existing code."
- "Strategy for task status transitions — each valid transition is its own class. No switch statements, each testable independently."
- "Command pattern in CLI — every Picocli command encapsulates action + parameters. Could be queued or logged trivially."

### CLI
- "The CLI is a completely separate Maven module that calls the same REST API — no special backend changes needed."
- "Offline mode uses SQLite locally. When the user runs sprintly sync, a SyncEngine reconciles local pending changes with the server, and prompts the user on conflicts."
- "The assignee selector in CLI fetches the developer list from the API and renders an arrow-key-navigable list — user never types an ID manually."