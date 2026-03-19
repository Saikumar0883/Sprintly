# 🏗️ Sprintly — Architecture Documentation

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                          CLIENT LAYER                           │
│            Swagger UI  /  Postman  /  Frontend App              │
└────────────────────────────┬────────────────────────────────────┘
                             │  HTTP REST + WebSocket
┌────────────────────────────▼────────────────────────────────────┐
│                       GATEWAY LAYER                             │
│                     sprintly-gateway                             │
│                                                                 │
│  ┌─────────────────┐  ┌──────────────────┐  ┌───────────────┐  │
│  │  SwaggerConfig  │  │   CorsConfig     │  │
└   ─────────────────┘  └──────────────────┘  │
│  ┌──────────────────────────┐  ┌──────────────────────────────┐ │
│  │  RequestLoggingFilter    │  │  GlobalExceptionHandler      │ │
│  └──────────────────────────┘  └──────────────────────────────┘ │
└────────┬──────────────┬──────────────┬──────────────┬───────────┘
         │              │              │               │
┌────────▼───┐  ┌───────▼───┐  ┌──────▼──────┐ ┌─────▼──────────┐
│            │  │           │  │             │ │                 │
│sprintly-   │  │sprintly-  │  │sprintly-    │ │sprintly-        │
│auth        │  │user       │  │task         │ │notification     │
│            │  │           │  │             │ │                 │
│• Register  │  │• Profile  │  │• CRUD       │ │• WebSocket      │
│• Login     │  │• Roles    │  │• Assign     │ │• Real-time      │
│• JWT       │  │• Search   │  │• Status     │ │• In-app alerts  │
│• OAuth2    │  │           │  │• Comments   │ │                 │
│• Refresh   │  │           │  │• Strategy   │ │                 │
│• Logout    │  │           │  │  pattern    │ │                 │
└────────────┘  └───────────┘  └─────────────┘ └────────────────┘
         │              │              │               │
         └──────────────┴──────────────┴───────────────┘
                                │
              ┌─────────────────▼──────────────────┐
              │          sprintly-common           │
              │                                    │
              │  DTOs        Exceptions   Enums    │
              │  ApiResponse  Sprintly    TaskStatus│
              │  PagedResp    Resource    Priority  │
              │  ErrorResp    NotFound    UserRole  │
              │               Unauthorized          │
              │                                     │
              │  Patterns                Utilities  │
              │  Singleton  Factory      DateUtil   │
              │  AppConfig  Notification ValidUtil  │
              └────────────────────────────────────┘
                                │
                  ┌─────────────┐
            ┌─────▼──────┐
            │ PostgreSQL │
            │            │
            │ users      │
            │ refresh_tk │
            │ tasks      │
            └────────────┘
```

---

## Module Responsibilities

| Module                  | Type         | Responsibility                                      |
| ----------------------- | ------------ | --------------------------------------------------- |
| `sprintly-common`       | JAR library  | Shared DTOs, exceptions, enums, design patterns     |
| `sprintly-auth`         | JAR module   | JWT, OAuth2, login/register/refresh/logout          |
| `sprintly-user`         | JAR module   | User entity, profile management, role assignment    |
| `sprintly-task`         | JAR module   | Task CRUD, assignment, status transitions, comments |
| `sprintly-notification` | JAR module   | WebSocket config, real-time notifications           |
| `sprintly-gateway`      | **Runnable** | Orchestrates all modules, Swagger, CORS, filters    |

> Only `sprintly-gateway` has `@SpringBootApplication` and produces a runnable fat JAR.

---

## Request Lifecycle

```
1. Client sends: POST /api/tasks  { "title": "Fix bug", ... }
                 Authorization: Bearer eyJhbGci...

2. RequestLoggingFilter          → logs: [REQUEST] POST /api/tasks

3. JwtAuthFilter                 → extracts token from header
                                 → validates signature + expiry
                                 → loads UserDetails from DB
                                 → sets SecurityContextHolder

4. SecurityConfig rules          → checks /api/tasks/** is authenticated ✓

5. TaskController.createTask()   → @PreAuthorize("hasRole('DEVELOPER')") ✓
                                 → @Valid validates request body

6. TaskService.createTask()      → business logic
                                 → saves to PostgreSQL via JDBC
                                 → notifies via NotificationService (WebSocket)

7. ApiResponse.success()         → wraps result in standard envelope

8. GlobalExceptionHandler        → (not triggered on success)

9. RequestLoggingFilter          → logs: [RESPONSE] POST /api/tasks → 201 (23ms)

10. Client receives:
    {
      "success": true,
      "message": "Task created",
      "data": { "id": 42, "title": "Fix bug", ... },
      "timestamp": "..."
    }
```

---

## Design Patterns

### Singleton — `AppConfigManager`

```
Problem : Multiple parts of the app need runtime config.
          Creating a new config object each time wastes memory
          and risks inconsistent state.

Solution: One shared instance, created lazily on first use.
          Thread-safe via double-checked locking + volatile.

Usage   : AppConfigManager.getInstance().get("app.name")
```

### Factory — `NotificationFactory`

```
Problem : Code that sends notifications shouldn't need to know
          whether it's IN_APP, EMAIL or PUSH — that's an
          implementation detail.

Solution: Pass a type enum to the factory; get back a Notification
          object with a .send() method. Adding a new type only
          requires a new class + one switch case.

Usage   : NotificationFactory.create(NotificationType.EMAIL)
                              .send(userId, subject, body);
```

### Builder — `ApiResponse`, `AuthResponse`, `ErrorResponse`

```
Problem : Constructors with many optional parameters are hard
          to read and maintain (telescoping constructor anti-pattern).

Solution: Builder pattern allows selective field setting with
          readable method chains. Lombok @Builder generates it.

Usage   : ApiResponse.<UserDTO>builder()
              .success(true)
              .message("User fetched")
              .data(userDto)
              .build();
```

### Strategy — `TaskStatusStrategy` (Phase 4)

```
Problem : Task status transitions have different validation rules.
          A giant if-else chain is hard to test and extend.

Solution: Each transition (e.g. TODO→IN_PROGRESS) is a separate
          strategy class implementing a common interface.
          The factory selects the right strategy at runtime.

Usage   : TaskStatusStrategyFactory
              .getStrategy(currentStatus, newStatus)
              .execute(task);
```

---

## Security Model

```
Endpoint                    │ Public │ Auth │ DEVELOPER │ MANAGER │ ADMIN
────────────────────────────┼────────┼──────┼───────────┼─────────┼──────
POST /api/auth/**           │  ✓     │      │           │         │
GET  /swagger-ui/**         │  ✓     │      │           │         │
GET  /actuator/health       │  ✓     │      │           │         │
────────────────────────────┼────────┼──────┼───────────┼─────────┼──────
GET  /api/tasks             │        │  ✓   │     ✓     │    ✓    │  ✓
POST /api/tasks             │        │      │     ✓     │    ✓    │  ✓
PATCH /api/tasks/{id}/status│        │      │     ✓     │    ✓    │  ✓
DELETE /api/tasks/{id}      │        │      │           │    ✓    │  ✓
POST /api/tasks/{id}/assign │        │      │           │    ✓    │  ✓
────────────────────────────┼────────┼──────┼───────────┼─────────┼──────
GET  /api/users             │        │      │           │    ✓    │  ✓
PUT  /api/users/{id}        │        │  ✓*  │           │         │  ✓
DELETE /api/users/{id}      │        │      │           │         │  ✓
────────────────────────────┼────────┼──────┼───────────┼─────────┼──────
*own profile only
```

---

## Technology Decisions

| Decision               | Choice              | Rationale                                      |
| ---------------------- | ------------------- | ---------------------------------------------- |
| Auth mechanism         | JWT (stateless)     | No session store needed; horizontally scalable |
| Password hashing       | BCrypt strength 12  | ~0.5s hash time; resistant to brute-force      |
| Token refresh strategy | Rotation            | Limits stolen token damage window              |
| Data Access            | Spring JDBC         | Direct SQL; fast queries; explicit control     |
| DTO mapping            | MapStruct           | Compile-time; no reflection overhead           |
| API documentation      | SpringDoc OpenAPI 3 | Auto-discovery; try-it-out in browser          |
| WebSocket protocol     | STOMP over WS       | Pub/sub semantics on top of raw WebSocket      |
| Database               | PostgreSQL only     | Sufficient performance without caching layer   |

---

## Development Phases

| Phase | Module                  | Status             | Key Features                          |
| ----- | ----------------------- | ------------------ | ------------------------------------- |
| 1     | `sprintly-common`       | ✅ Complete        | DTOs, exceptions, patterns, utils     |
| 2     | `sprintly-auth`         | ✅ Complete        | JWT, OAuth2, SecurityConfig           |
| 3     | `sprintly-user`         | 🔲 Basic structure | User entity, repository               |
| 4     | `sprintly-task`         | ✅ Complete        | Task CRUD, user authentication        |
| 5     | `sprintly-notification` | ✅ Complete        | WebSocket, REST API, real-time alerts |
| 6     | `sprintly-gateway`      | ✅ Complete        | Swagger, CORS, filters, exception hdl |
