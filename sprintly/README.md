# 🗂️ Sprintly — Real-Time Collaborative Task Management System

> A modular, enterprise-grade Spring Boot backend with JWT Auth, WebSocket, REST APIs, OpenAPI/Swagger, and clean LLD design patterns.

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        CLIENT                               │
│          (Swagger UI / Postman / Web Browser)               │
└──────────────────────┬──────────────────────────────────────┘
                       │ HTTP / WebSocket
┌──────────────────────▼──────────────────────────────────────┐
│                   sprintly-gateway                           │
│         (Orchestrator — ONLY runnable Spring Boot app)       │
│   SwaggerConfig · CorsConfig · RequestLoggingFilter          │
│   GlobalExceptionHandler                                     │
└──────┬──────────────┬───────────────┬────────────────┬──────┘
       │              │               │                │
┌──────▼──────┐ ┌─────▼──────┐ ┌─────▼──────┐ ┌──────▼──────┐
│sprintly-auth│ │sprintly-   │ │sprintly-   │ │sprintly-    │
│             │ │user        │ │task        │ │notification │
│ JWT         │ │Profile     │ │CRUD · State│ │WebSocket    │
│ Refresh tkn │ │Roles       │ │Comments    │ │Real-time    │
└─────────────┘ └────────────┘ └────────────┘ └─────────────┘
                       │ all share
┌──────────────────────▼──────────────────────────────────────┐
│                   sprintly-common                            │
│   ApiResponse · ErrorResponse · PagedResponse (Builder)      │
│   SprintlyException hierarchy                                │
│   AppConfigManager (Singleton) · NotificationFactory (Factory)│
│   TaskStatus · TaskPriority · UserRole enums                 │
└──────────────────────────────────────────────────────────────┘
                       │
             ┌─────────┴─────────┐
       ┌─────▼──────┐
       │ PostgreSQL │
       │  (main DB) │
       └────────────┘
```

---

## 📁 Folder Structure

```
sprintly/
├── pom.xml                                   ← Parent POM
│
├── sprintly-common/                          ← Shared library (plain JAR)
│   └── src/main/java/com/sprintly/common/
│       ├── dto/
│       │   ├── ApiResponse.java              ← Builder pattern
│       │   ├── PagedResponse.java            ← Builder pattern
│       │   └── ErrorResponse.java            ← Builder pattern
│       ├── exception/
│       │   ├── SprintlyException.java        ← Base exception
│       │   ├── ResourceNotFoundException.java
│       │   ├── UnauthorizedException.java
│       │   └── BadRequestException.java
│       ├── enums/
│       │   ├── TaskStatus.java
│       │   ├── TaskPriority.java
│       │   └── UserRole.java
│       ├── patterns/
│       │   ├── singleton/
│       │   │   └── AppConfigManager.java     ← Singleton pattern
│       │   └── factory/
│       │       ├── Notification.java         ← Factory pattern
│       │       ├── NotificationFactory.java
│       │       └── NotificationType.java
│       └── util/
│           ├── DateUtil.java
│           └── ValidationUtil.java
│
├── sprintly-auth/
│   └── src/main/java/com/sprintly/auth/
│       ├── controller/AuthController.java
│       ├── service/AuthService.java
│       ├── service/JwtService.java
│       ├── security/SecurityConfig.java
│       ├── security/JwtAuthFilter.java
│       ├── entity/RefreshToken.java
│       ├── dto/LoginRequest.java
│       ├── dto/RegisterRequest.java
│       └── dto/AuthResponse.java
│
├── sprintly-user/
│   └── src/main/java/com/sprintly/user/
│       ├── controller/UserController.java
│       ├── service/UserService.java
│       ├── entity/User.java
│       ├── dto/UserDTO.java
│       ├── dto/UpdateUserRequest.java
│       └── mapper/UserMapper.java
│
├── sprintly-task/
│   └── src/main/java/com/sprintly/task/
│       ├── controller/TaskController.java
│       ├── controller/CommentController.java
│       ├── service/TaskService.java
│       ├── service/CommentService.java
│       ├── entity/Task.java
│       ├── entity/Comment.java
│       ├── dto/TaskDTO.java
│       ├── dto/CreateTaskRequest.java
│       ├── dto/UpdateTaskRequest.java
│       ├── mapper/TaskMapper.java
│       └── strategy/                         ← Strategy pattern (status transitions)
│           ├── TaskStatusStrategy.java
│           ├── TodoToInProgressStrategy.java
│           └── TaskStatusStrategyFactory.java
│
├── sprintly-notification/
│   └── src/main/java/com/sprintly/notification/
│       ├── controller/NotificationController.java
│       ├── service/NotificationService.java
│       ├── entity/Notification.java
│       ├── repository/NotificationRepository.java
│       ├── repository/JdbcNotificationRepository.java
│       ├── dto/NotificationDTO.java
│       ├── mapper/NotificationMapper.java
│       └── websocket/
│           ├── WebSocketConfig.java
│           └── WebSocketEventListener.java
│
└── sprintly-gateway/
    └── src/main/java/com/sprintly/gateway/
        ├── GatewayApplication.java           ← Main entry point
        ├── config/SwaggerConfig.java
        ├── config/CorsConfig.java
        ├── filter/RequestLoggingFilter.java
        └── exception/GlobalExceptionHandler.java
```

---

## 🧠 Design Patterns

| Pattern   | Class                 | Why                                                   |
| --------- | --------------------- | ----------------------------------------------------- |
| Singleton | `AppConfigManager`    | One config instance across JVM, thread-safe lazy init |
| Factory   | `NotificationFactory` | Create IN_APP/EMAIL/PUSH without coupling to impl     |
| Builder   | `ApiResponse<T>`      | Readable, consistent response construction            |
| Builder   | `ErrorResponse`       | Structured error bodies from GlobalExceptionHandler   |
| Strategy  | `TaskStatusStrategy`  | Each status transition encapsulates its own rules     |

---

## 🛠️ Tech Stack

| Layer     | Technology                            |
| --------- | ------------------------------------- |
| Framework | Spring Boot 3.2.x, Java 17            |
| Security  | Spring Security 6, JWT (jjwt)         |
| Database  | PostgreSQL 15 + Spring JDBC + Flyway  |
| Real-time | Spring WebSocket + STOMP              |
| API Docs  | SpringDoc OpenAPI 3 (Swagger UI)      |
| Mapping   | MapStruct                             |
| Build     | Maven Multi-module                    |
| Testing   | JUnit 5, Mockito, Testcontainers      |

---

## 🚀 Running the App

```bash
# 1. Start PostgreSQL
docker-compose up -d postgres

# 2. Build all modules using Maven
mvn clean install -DskipTests

# 3. Run from gateway module
# (Flyway will automatically initialize the database schema)
cd sprintly-gateway
mvn spring-boot:run
```

### 💬 Interactive CLI REPL
The `sprintly-cli` now features an interactive REPL mode (similar to Claude Code CLI) for enhanced user experience. 
**New!** All commands now support step-by-step interactive prompting for missing arguments.

```bash
# Run the CLI interactively
java -jar sprintly-cli/target/sprintly-cli-1.0.0-SNAPSHOT-jar-with-dependencies.jar

# This will drop you into the interactive prompt where you can type commands:
sprintly> login
Please enter your login details.
Enter your email: admin@sprintly.com
Enter your password: *****
...
sprintly> task create
Enter task title: Fix the DB
Enter task description: The DB is down
Fetching users...
1. Alice (alice@sprintly.com)
Enter the number to assign this task: 1
```

### Access Points

| Service    | URL                                   |
| ---------- | ------------------------------------- |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| API Base   | http://localhost:8080/api             |
| WebSocket  | ws://localhost:8080/ws                |
| Health     | http://localhost:8080/actuator/health |

---

## 🔐 API Quick Reference

### Auth

| Method | Endpoint           | Auth   |
| ------ | ------------------ | ------ |
| POST   | /api/auth/register | Public |
| POST   | /api/auth/login    | Public |
| POST   | /api/auth/refresh  | Public |
| POST   | /api/auth/logout   | JWT    |

### Tasks

| Method | Endpoint                        | Role       |
| ------ | ------------------------------- | ---------- |
| GET    | /api/tasks                      | Auth       |
| POST   | /api/tasks                      | Auth       |
| PATCH  | /api/tasks/{id}/status          | Auth       |
| DELETE | /api/tasks/{id}                 | Auth       |
| POST   | /api/tasks/{id}/assign/{userId} | Auth       |

### Notifications

| Method | Endpoint                        | Role |
| ------ | ------------------------------- | ---- |
| GET    | /api/notifications              | Any  |
| GET    | /api/notifications/unread       | Any  |
| GET    | /api/notifications/unread/count | Any  |
| PUT    | /api/notifications/{id}/read    | Any  |
| PUT    | /api/notifications/read-all     | Any  |
| DELETE | /api/notifications/{id}         | Any  |
| DELETE | /api/notifications              | Any  |

---

## 📋 Development Phases

| Phase | Module                | Status             |
| ----- | --------------------- | ------------------ |
| 1     | sprintly-common       | ✅ Complete        |
| 2     | sprintly-auth         | ✅ Complete        |
| 3     | sprintly-user         | 🔲 Basic structure |
| 4     | sprintly-task         | ✅ Complete        |
| 5     | sprintly-notification | ✅ Complete        |
| 6     | sprintly-gateway      | ✅ Complete        |
