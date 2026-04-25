# ⚡ Sprintly — Real-Time Collaborative Task Management

> A modular Spring Boot 3 backend with JWT auth, WebSocket notifications, and a full-featured terminal CLI client. Built with clean LLD design patterns on a multi-module Maven architecture.

[![Java](https://img.shields.io/badge/Java-17-orange)](https://adoptium.net)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.4-green)](https://spring.io/projects/spring-boot)
[![Maven](https://img.shields.io/badge/Maven-Multi--module-blue)](https://maven.apache.org)
[![Docker](https://img.shields.io/badge/Docker-Compose-blue)](https://docker.com)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

---

## Table of Contents

- [What is Sprintly](#what-is-sprintly)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Setup — Backend with Docker](#setup--backend-with-docker)
- [Setup — CLI](#setup--cli)
- [Getting Updates](#getting-updates)
- [CLI Command Reference](#cli-command-reference)
- [API Reference](#api-reference)
- [Database Schema](#database-schema)
- [Design Patterns](#design-patterns)
- [Development Phases](#development-phases)
- [Access Points](#access-points)

---

## What is Sprintly

Sprintly is a real-time collaborative task management system with two parts:

**Backend** — a Spring Boot REST API with WebSocket support, JWT authentication, and PostgreSQL. Runs inside Docker.

**CLI** — a terminal client built with Picocli and JLine. Runs on your host machine. Connects to the backend over HTTP. Features a REPL with Tab completion, persistent history, Kanban board view, inline text editing, and real-time notification badges.

```
You type:   sprintly
            sprintly> /tasks          ← slash shortcuts with Tab completion
            sprintly> task board      ← Kanban view
            sprintly> task status 3   ← forward-only status transitions
            sprintly> notification unread
```

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                          CLIENT LAYER                               │
│   sprintly CLI (terminal)      Swagger UI          Postman          │
└───────────┬────────────────────────┬─────────────────────┬─────────┘
            │  HTTP REST             │  HTTP + WebSocket   │
            │  Apache HttpClient 5   │                     │
┌───────────▼────────────────────────▼─────────────────────▼─────────┐
│                    sprintly-gateway  :8080                          │
│         SwaggerConfig · CorsConfig · RequestLoggingFilter           │
│         GlobalExceptionHandler                                      │
└────┬─────────────┬──────────────┬─────────────────┬────────────────┘
     │             │              │                 │
┌────▼────┐  ┌─────▼────┐  ┌─────▼──────┐  ┌──────▼─────────────────┐
│  auth   │  │  user    │  │  task      │  │  notification          │
│         │  │          │  │            │  │                        │
│Register │  │List      │  │CRUD        │  │WebSocket (STOMP)       │
│Login    │  │Find by   │  │Reporter    │  │Real-time push          │
│Refresh  │  │email/id  │  │Assignee    │  │Persist to DB           │
│Logout   │  │          │  │Bulk update │  │Read / Unread           │
│JWT      │  │          │  │Forward-only│  │                        │
└─────────┘  └──────────┘  └────────────┘  └────────────────────────┘
                                 │
                  ┌──────────────▼──────────────┐
                  │        sprintly-common       │
                  │  ApiResponse   (Builder)     │
                  │  ErrorResponse (Builder)     │
                  │  TaskFlowException hierarchy │
                  │  AppConfigManager (Singleton)│
                  │  NotificationFactory (Factory│
                  └──────────────┬──────────────┘
                                 │
                          ┌──────▼──────┐
                          │ PostgreSQL  │
                          │ users       │
                          │ tasks       │
                          │ notifications│
                          │ refresh_tkn │
                          └─────────────┘
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 LTS |
| Framework | Spring Boot 3.2.4 |
| Security | Spring Security 6 + JWT (jjwt) |
| Database | PostgreSQL 16 + JdbcTemplate (raw SQL, no JPA/Hibernate) |
| Migrations | Flyway |
| Real-time | Spring WebSocket + STOMP |
| API Docs | SpringDoc OpenAPI 3 (Swagger UI) |
| Build | Maven Multi-module |
| CLI Framework | Picocli 4.x + JLine 3 |
| CLI HTTP | Apache HttpClient 5 |
| Containerization | Docker + Docker Compose |

---

## Project Structure

```
sprintly/
├── Dockerfile                              ← Multi-stage backend image
├── docker-compose.yml                      ← Orchestrates backend + database
├── .env                                    ← Secrets — never commit to git
├── .env.example                            ← Template — safe to commit
├── .dockerignore                           ← Keeps Docker builds fast
│
├── sprintly-common/                        ← Shared library (plain JAR, no Spring)
│   └── src/main/java/com/sprintly/common/
│       ├── dto/
│       │   ├── ApiResponse.java            ← Builder pattern
│       │   ├── ErrorResponse.java          ← Builder pattern
│       │   └── UserDTO.java
│       ├── exception/
│       │   ├── TaskFlowException.java      ← Base exception
│       │   ├── ResourceNotFoundException.java
│       │   ├── UnauthorizedException.java
│       │   └── BadRequestException.java
│       ├── enums/
│       │   ├── TaskStatus.java
│       │   └── TaskPriority.java
│       └── patterns/
│           ├── singleton/AppConfigManager.java   ← Singleton pattern
│           └── factory/NotificationFactory.java  ← Factory pattern
│
├── sprintly-auth/                          ← JWT authentication
│   └── src/main/java/com/sprintly/auth/
│       ├── controller/AuthController.java
│       ├── service/AuthService.java
│       ├── service/JwtService.java
│       ├── service/CustomUserDetailsService.java
│       ├── security/SecurityConfig.java
│       ├── security/JwtAuthFilter.java
│       ├── entity/RefreshToken.java
│       └── repository/JdbcRefreshTokenRepository.java
│
├── sprintly-user/                          ← User management
│   └── src/main/java/com/sprintly/user/
│       ├── controller/UserController.java
│       ├── entity/User.java
│       └── repository/JdbcUserRepository.java
│
├── sprintly-task/                          ← Task CRUD + status + bulk update
│   └── src/main/java/com/sprintly/task/
│       ├── controller/TaskController.java
│       ├── service/TaskService.java        ← forward-only transitions, assignee restriction
│       ├── entity/Task.java               ← reporter + assignee fields
│       ├── dto/
│       │   ├── TaskDTO.java               ← reporterId, reporterName, assigneeName
│       │   ├── CreateTaskRequest.java
│       │   ├── UpdateTaskRequest.java
│       │   ├── BulkStatusRequest.java     ← bulk status update
│       │   └── BulkStatusResult.java
│       ├── mapper/TaskMapper.java
│       └── repository/JdbcTaskRepository.java  ← LEFT JOIN for names
│
├── sprintly-notification/                  ← WebSocket + REST notifications
│   └── src/main/java/com/sprintly/notification/
│       ├── controller/NotificationController.java
│       ├── service/NotificationService.java
│       ├── entity/Notification.java
│       ├── dto/NotificationDTO.java
│       ├── mapper/NotificationMapper.java
│       ├── repository/JdbcNotificationRepository.java
│       └── websocket/
│           ├── WebSocketConfig.java
│           └── WebSocketEventListener.java
│
├── sprintly-gateway/                       ← ONLY runnable @SpringBootApplication
│   └── src/main/java/com/sprintly/gateway/
│       ├── GatewayApplication.java
│       ├── config/
│       │   ├── SwaggerConfig.java
│       │   └── CorsConfig.java
│       ├── filter/RequestLoggingFilter.java
│       └── exception/GlobalExceptionHandler.java
│   └── src/main/resources/
│       ├── application.yml
│       └── db/migration/V1__init.sql       ← Flyway schema (auto-runs on startup)
│
└── sprintly-cli/                           ← Terminal client
    └── src/main/java/com/sprintly/cli/
        ├── SprintlyCli.java                ← main() + REPL + Tab completion + history
        ├── client/SprintlyClient.java      ← GET, POST, PUT, PATCH
        ├── config/CliConfig.java           ← ~/.sprintly-cli.json
        ├── util/CliPrompt.java             ← prompts + inline editing (JLine)
        └── command/
            ├── LoginCommand.java           ← shows Kanban on login
            ├── LogoutCommand.java
            ├── RegisterCommand.java
            ├── RefreshCommand.java
            ├── task/
            │   ├── TaskCommand.java
            │   ├── ListTasksCommand.java       ← ID, Title, Status, Reporter, Assignee
            │   ├── BoardCommand.java           ← Kanban board view
            │   ├── CreateTaskCommand.java
            │   ├── GetTaskCommand.java         ← full details, word-wrapped
            │   ├── UpdateTaskCommand.java      ← inline editing, pre-filled text
            │   ├── UpdateTaskStatusCommand.java ← forward-only, assignee only
            │   └── BulkStatusCommand.java      ← update multiple tasks at once
            └── notification/
                ├── NotificationCommand.java
                ├── ListNotificationsCommand.java
                ├── UnreadNotificationsCommand.java
                ├── MarkReadCommand.java
                └── MarkAllReadCommand.java
```

---

## Prerequisites

| Tool | Version | Install |
|---|---|---|
| Docker | 24+ | https://docker.com/get-started |
| Docker Compose | v2 | included with Docker Desktop |
| Java | 17 LTS | https://adoptium.net |
| Maven | 3.9+ | https://maven.apache.org or `brew install maven` |

---

## Setup — Backend with Docker

The backend (Spring Boot + PostgreSQL) runs inside Docker containers.

### Step 1 — Clone the repository

```bash
git clone https://github.com/Saikumar0883/Sprintly
cd Sprintly/sprintly
```

### Step 2 — Configure environment

```bash
cp .env.example .env
```

Open `.env` and set these values:

```env
POSTGRES_PASSWORD=your_strong_password_here
JWT_SECRET=your_minimum_64_character_secret_here
DB_PORT=5433          # change if 5432 is already in use on your machine
APP_PORT=8080
```

Generate a strong JWT secret:
```bash
openssl rand -base64 64
```

### Step 3 — Start the backend

```bash
docker-compose up -d --build
```

First run takes ~3 minutes (builds the Spring Boot image).
Subsequent runs are instant (uses Docker layer cache).

This automatically:
- Builds the backend image from source
- Starts PostgreSQL on your configured port
- Starts the Spring Boot app on port 8080
- Runs Flyway migrations — all tables created automatically

### Step 4 — Verify everything is healthy

```bash
docker-compose ps
```

Both containers must show `(healthy)`:
```
NAME            IMAGE                 STATUS              PORTS
sprintly-app    sprintly-app:latest   Up (healthy)        0.0.0.0:8080->8080/tcp
sprintly-db     postgres:16-alpine    Up (healthy)        0.0.0.0:5433->5432/tcp
```

```bash
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP","components":{"db":{"status":"UP"}}}
```

### Docker day-to-day commands

```bash
docker-compose up -d                         # start everything
docker-compose down                          # stop (data preserved)
docker-compose down -v                       # stop + wipe all data (fresh start)
docker-compose up -d --build sprintly-app   # rebuild after code changes
docker-compose logs -f sprintly-app         # watch backend logs live
docker-compose logs -f sprintly-db          # watch database logs
docker-compose ps                            # check status
```

---

## Setup — CLI

The CLI runs on your host machine and connects to the backend at `http://localhost:8080`.
The backend must be running before using the CLI.

### Build and install

```bash
# From the project root (same repo, no separate clone needed)
mvn clean install -DskipTests
```

This compiles all modules and automatically creates a launch script at `~/.local/bin/sprintly`.

Add `~/.local/bin` to your PATH if it isn't already (add to `~/.bashrc` or `~/.zshrc`):

```bash
echo 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.bashrc
source ~/.bashrc
```

### Run

```bash
sprintly
```

You will see:
```
  ╔════════════════════════════════════════════════╗
  ║   ⚡  Sprintly CLI  —  Task Management      ║
  ╚════════════════════════════════════════════════╝

  Type /    then Tab  →  see all slash commands
  Press ↑↓           →  navigate command history
  Type help or /exit  →  get help or quit

sprintly>
```

### First time

```
sprintly> register        ← create your account
sprintly> task board      ← see the Kanban board
sprintly> task create     ← create your first task
```

---

## Getting Updates

When new code is pushed to the repository:

```bash
git pull                          # get latest source code
docker-compose up -d --build      # rebuild and restart backend
mvn clean install -DskipTests     # rebuild CLI
```

The `sprintly` command is updated automatically by the Maven build.

---

## CLI Command Reference

### Auth

| Command | Description |
|---|---|
| `register` | Create a new account |
| `login` | Login — Kanban board shown automatically on success |
| `logout` | Logout from all devices, clears saved token |
| `refresh` | Refresh expired access token without re-login |

### Tasks

| Command | Description |
|---|---|
| `task list` | All tasks — ID, Title, Status, Reporter, Assignee |
| `task list --status TODO` | Filter by status |
| `task board` | Kanban board across all status columns |
| `task create` | Create a task — you become the Reporter automatically |
| `task get <id>` | Full task details — description fully shown, not truncated |
| `task update <id>` | Edit title/description — existing text pre-filled for inline editing |
| `task status <id>` | Change status — interactive menu of valid forward transitions |
| `task status <id> DONE` | Change status directly without menu |
| `task bulk-status` | Update multiple tasks to the same status at once |

**Status transition rules:**

```
Rank order:  TODO(0) → IN_PROGRESS(1) → IN_REVIEW(2) → DONE(3)

Allowed:     any forward jump — TODO → DONE is valid (skip steps)
Blocked:     any backward move — IN_REVIEW → TODO is blocked
Terminal:    DONE and CANCELLED → no further changes
Special:     CANCELLED available from any non-terminal state

Restriction: only the ASSIGNEE can change task status
Notification: when moved to DONE → REPORTER is notified automatically
```

### Notifications

| Command | Description |
|---|---|
| `notification unread` | Show unread notifications, prompts to mark all as read |
| `notification list` | Show all notifications (read ✓ and unread 🔔) |
| `notification read <id>` | Mark one notification as read |
| `notification read-all` | Mark all as read at once |

### Slash shortcuts (type `/` then Tab to autocomplete)

| Shortcut | Equivalent full command |
|---|---|
| `/tasks` | `task list` |
| `/board` | `task board` |
| `/create` | `task create` |
| `/get <id>` | `task get <id>` |
| `/update <id>` | `task update <id>` |
| `/status <id>` | `task status <id>` |
| `/bulk` | `task bulk-status` |
| `/n` | `notification unread` |
| `/notifications` | `notification unread` |
| `/notif-list` | `notification list` |
| `/read-all` | `notification read-all` |
| `/whoami` | Show current user + unread count |
| `/clear` | Clear terminal screen |
| `/help` | Full command guide |
| `/exit` | Exit the REPL |

### REPL features

| Feature | How |
|---|---|
| Tab completion | Type `/` then press Tab — shows and completes slash commands |
| Command history | Press ↑ ↓ — persists across sessions in `~/.sprintly_history` |
| Unread badge | Prompt shows `sprintly [3 unread]>` when notifications are waiting |
| Inline editing | `task update` pre-fills existing text — edit in place, not retype |
| Kanban on login | Board shown automatically after every successful login |

### Session file

```
~/.sprintly-cli.json     stores access token, refresh token, name, email
~/.sprintly_history      command history (persists across sessions)
```

Force a fresh login:
```bash
rm ~/.sprintly-cli.json
```

---

## API Reference

All endpoints except auth require `Authorization: Bearer <token>` header.

Swagger UI: `http://localhost:8080/swagger-ui.html`

### Auth — `/api/auth`

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/register` | Public | Register new account, returns JWT tokens |
| POST | `/login` | Public | Login, returns JWT tokens |
| POST | `/refresh` | Public | Rotate refresh token, get new access token |
| POST | `/logout` | JWT | Revoke all refresh tokens for this user |

### Users — `/api/users`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/` | JWT | List all users (used by CLI for assignee selection) |

### Tasks — `/api/tasks`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/` | JWT | All tasks with reporter + assignee names via SQL JOIN |
| GET | `/my-tasks` | JWT | Tasks assigned to the authenticated user |
| GET | `/{id}` | JWT | Single task full details |
| POST | `/` | JWT | Create task — authenticated user becomes reporter |
| PUT | `/{id}` | JWT | Update title, description, assignedTo |
| PATCH | `/{id}/status` | JWT — assignee only | Change task status (forward only) |
| PATCH | `/bulk-status` | JWT — assignee only | Update multiple tasks at once |
| DELETE | `/{id}` | JWT | Delete task permanently |

### Notifications — `/api/notifications`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/` | JWT | All notifications for current user |
| GET | `/unread` | JWT | Unread notifications only |
| GET | `/unread/count` | JWT | Count of unread (used by CLI badge) |
| PUT | `/{id}/read` | JWT | Mark one notification as read |
| PUT | `/read-all` | JWT | Mark all as read |
| DELETE | `/{id}` | JWT | Delete one notification |
| DELETE | `/` | JWT | Delete all notifications |

### WebSocket

```
Endpoint:   ws://localhost:8080/ws

Subscribe:  /user/queue/notifications        personal real-time alerts
Subscribe:  /user/queue/notification-count   unread count for badge
Subscribe:  /topic/notifications             broadcast to all users
```

---

## Database Schema

```sql
-- Central identity table
users (
  id          BIGSERIAL PRIMARY KEY,
  name        VARCHAR(100) NOT NULL,
  email       VARCHAR(150) NOT NULL UNIQUE,
  password    VARCHAR(255) NOT NULL,
  enabled     BOOLEAN NOT NULL DEFAULT true,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
)

-- Persisted refresh tokens for rotation and revocation
refresh_tokens (
  id          BIGSERIAL PRIMARY KEY,
  token       VARCHAR(500) NOT NULL UNIQUE,
  user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  expires_at  TIMESTAMP NOT NULL,
  revoked     BOOLEAN NOT NULL DEFAULT false,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
)

-- Tasks with reporter (creator) and assignee
tasks (
  id           BIGSERIAL PRIMARY KEY,
  title        VARCHAR(200) NOT NULL,
  description  TEXT,
  status       VARCHAR(30) NOT NULL DEFAULT 'TODO',
  created_by   BIGINT NOT NULL REFERENCES users(id),  -- reporter
  assigned_to  BIGINT REFERENCES users(id),           -- assignee (nullable)
  created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
)

-- Real-time and persistent notifications
notifications (
  id           BIGSERIAL PRIMARY KEY,
  type         VARCHAR(50) NOT NULL,   -- TASK_ASSIGNED, TASK_DONE
  title        VARCHAR(200) NOT NULL,
  message      TEXT,
  recipient_id BIGINT NOT NULL REFERENCES users(id),
  sender_id    BIGINT REFERENCES users(id),
  entity_id    BIGINT,
  entity_type  VARCHAR(50),
  read         BOOLEAN NOT NULL DEFAULT false,
  created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  read_at      TIMESTAMP
)
```

**Automatic notification triggers:**

| Event | Recipient | Type |
|---|---|---|
| Task assigned to a user | Assignee | `TASK_ASSIGNED` |
| Assignee moves task to DONE | Reporter | `TASK_DONE` |

---

## Design Patterns

| Pattern | Class | Why |
|---|---|---|
| **Singleton** | `AppConfigManager` | Thread-safe double-checked locking, framework-agnostic config access |
| **Factory** | `NotificationFactory` | Create IN_APP/EMAIL/PUSH channels without coupling to implementations |
| **Builder** | `ApiResponse<T>`, `AuthResponse`, `ErrorResponse` | Readable immutable object construction, consistent API responses |
| **Strategy** | `TaskService.isValidTransition()` | Rank-based rules — each status has a numeric rank, forward = higher rank only |
| **Command** | Every Picocli `@Command` class | Encapsulates action + parameters — Command pattern applied literally |
| **Observer** | `TaskService` → `NotificationService` → WebSocket | Task state changes push real-time notifications to subscribers |

---

## Development Phases

| Phase | Module | Status | Key Deliverables |
|---|---|---|---|
| 1 | `sprintly-common` | ✅ Complete | DTOs, exceptions, enums, Singleton, Factory patterns |
| 2 | `sprintly-auth` | ✅ Complete | JWT access + refresh tokens, token rotation, Spring Security |
| 3 | `sprintly-user` | ✅ Complete | User entity, JDBC repository, list endpoint |
| 4 | `sprintly-task` | ✅ Complete | CRUD, reporter/assignee, bulk status, forward-only transitions |
| 5 | `sprintly-notification` | ✅ Complete | WebSocket STOMP, JDBC repository, full REST API |
| 6 | `sprintly-gateway` | ✅ Complete | Swagger UI, CORS, request logging, global exception handler |
| 7 | `sprintly-cli` | ✅ Complete | Auth + Task + Notification commands, REPL, Kanban board |
| 8 | Docker | ✅ Complete | Multi-stage image, docker-compose, health checks, env config |

---

## Access Points

| Service | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| API Base URL | http://localhost:8080/api |
| Health Check | http://localhost:8080/actuator/health |
| WebSocket | ws://localhost:8080/ws |
| CLI session | ~/.sprintly-cli.json |
| CLI history | ~/.sprintly_history |