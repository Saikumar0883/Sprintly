# ♊ GEMINI.md — Sprintly Project Mandates

> This file contains foundational mandates and technical standards for Gemini CLI when working on the Sprintly project.
> These instructions take absolute precedence over general defaults.

---

## 🏗️ Core Architecture & Tech Stack
- **Framework:** Spring Boot 3.2.x, Java 17 (LTS).
- **Build System:** Multi-module Maven. Always run `mvn clean install` from the root when making cross-module changes.
- **Entry Point:** `sprintly-gateway` is the ONLY runnable module. Other modules are plain JARs.
- **Persistence:** PostgreSQL 15 (Spring JDBC) and Redis (JWT Blacklist/Caching).
- **Security:** Spring Security 6 with JWT (stateless) and stateful Refresh Tokens (revocable).
- **Messaging:** Spring WebSocket + STOMP for real-time notifications.
- **CLI:** Picocli + Lanterna TUI in `sprintly-cli`.

---

## 🎨 Design Patterns & Coding Standards
Rigorously adhere to these patterns as established in the project:
- **Singleton:** Use for shared configuration (e.g., `AppConfigManager`) with thread-safe lazy initialization.
- **Factory:** Use for creating notifications (`NotificationFactory`) and strategies.
- **Builder:** ALWAYS use for DTOs and API responses (`ApiResponse`, `ErrorResponse`).
- **Strategy:** Use for complex state transitions, such as task status updates (`TaskStatusStrategy`).
- **Command:** Use for CLI operations and complex business actions.
- **Observer:** Use for real-time WebSocket pushes triggered by backend events.

**Coding Style:**
- Follow standard Spring Boot/Java naming conventions (camelCase for variables/methods, PascalCase for classes).
- Use MapStruct for DTO-to-Entity mapping.
- Keep controllers lean; delegate business logic to services.
- Utilize the `sprintly-common` module for shared exceptions, enums, and utility classes.

---

## 🛠️ Development & Validation Workflow
1. **Research:** Analyze the impact across modules (e.g., a change in `sprintly-common` affects almost everything).
2. **Implementation:**
   - **Surgical Updates:** Modify only what's necessary.
   - **Idiomatic Quality:** Ensure changes match the existing architectural patterns.
   - **CLI Alignment:** For every new backend feature, assess if a corresponding command or TUI update is needed in `sprintly-cli`.
3. **Testing:**
   - **Mandatory Tests:** All new features or bug fixes MUST include JUnit 5 tests.
   - **Mocking:** Use Mockito for service-level unit tests.
   - **Integration:** Use Testcontainers for repository-level tests where possible.
4. **Validation:**
   - Run `mvn clean install` to ensure no cross-module regressions.
   - Verify API changes via Swagger (`/swagger-ui.html`) if the application can be run.
   - For CLI changes, verify the interactive prompts and offline sync logic.

---

## 🔐 Security & Safety
- **Secrets:** NEVER hardcode secrets. Use `application.yml` placeholders or environment variables.
- **Authentication:** Ensure all new endpoints are correctly configured in `SecurityConfig` (Public vs. Auth vs. Role-based).
- **Data Integrity:** Validate `assigneeId` roles (must be `ROLE_DEVELOPER`) and check for `ResourceNotFoundException`.

---

## 📋 Module Responsibilities
- `sprintly-common`: Shared libraries, base exceptions, global enums.
- `sprintly-auth`: Security filters, JWT/OAuth2 logic, user details service.
- `sprintly-user`: Profile management and role-based lookups.
- `sprintly-task`: Core JIRA-like logic (CRUD, comments, status strategy).
- `sprintly-notification`: Real-time WebSocket delivery and persistence.
- `sprintly-gateway`: Orchestration, Swagger, CORS, Global Exception Handling.
- `sprintly-cli`: Terminal client, interactive TUI, offline SQLite storage.
