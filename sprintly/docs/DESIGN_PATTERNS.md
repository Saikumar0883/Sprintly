# 🧠 Design Patterns — Implementation Guide

This document explains each design pattern used in Sprintly,
why it was chosen, and how to discuss it in an interview.

---

## 1. Singleton — `AppConfigManager`

**File:** `sprintly-common/patterns/singleton/AppConfigManager.java`

### What it does
Maintains a single shared configuration store across the entire JVM.

### Implementation
```java
// Double-checked locking (thread-safe lazy initialization)
public static AppConfigManager getInstance() {
    if (instance == null) {                    // Fast path — no lock needed
        synchronized (AppConfigManager.class) {
            if (instance == null) {            // Safe path — under lock
                instance = new AppConfigManager();
            }
        }
    }
    return instance;
}
```

### Why `volatile`?
Without `volatile`, the JVM may reorder instructions. Thread A could write
a partially-constructed object to `instance` before the constructor finishes.
Thread B sees a non-null `instance` and uses the incomplete object.
`volatile` forces a happens-before guarantee.

### Interview Q&A
- **"Why not use an enum singleton?"**
  Enum singletons are the safest but don't allow lazy initialization or
  custom constructor logic. Double-checked locking gives us both.
- **"Why not just use Spring's @Component (which is already a singleton)?"**
  Spring singletons are framework-managed. This pattern works outside the
  Spring context (e.g. in static helpers, test utilities, or early startup).

---

## 2. Factory — `NotificationFactory`

**File:** `sprintly-common/patterns/factory/NotificationFactory.java`

### What it does
Creates the correct notification implementation without the caller
knowing the concrete class.

### Implementation
```java
public static Notification create(NotificationType type) {
    return switch (type) {
        case IN_APP -> new InAppNotification();
        case EMAIL  -> new EmailNotification();
        case PUSH   -> new PushNotification();
    };
}
```

### Adding a new notification type (Open/Closed Principle)
1. Add `SMS` to `NotificationType` enum
2. Create `SmsNotification implements Notification`
3. Add `case SMS -> new SmsNotification()` to the factory

Zero changes to existing code. ✓

### Interview Q&A
- **"What's the difference between Factory and Abstract Factory?"**
  Factory creates one type of object. Abstract Factory creates families
  of related objects. Here we only need one — Factory is appropriate.
- **"Why not just use `new InAppNotification()` directly?"**
  Caller code would be coupled to the concrete class. If we rename it
  or change the constructor, all callers break. The factory is one place
  to update.

---

## 3. Builder — `ApiResponse`, `AuthResponse`, `ErrorResponse`

**File:** `sprintly-common/dto/ApiResponse.java`

### What it does
Constructs complex objects with many optional fields without
telescoping constructors.

### Implementation (Lombok @Builder)
```java
// Instead of: new ApiResponse(true, "OK", data, null, LocalDateTime.now())
ApiResponse.<UserDTO>builder()
    .success(true)
    .message("User fetched")
    .data(userDto)
    .build();  // timestamp filled by @Builder.Default
```

### Interview Q&A
- **"Why not just use a constructor with all parameters?"**
  With 5+ parameters, calls like `new AuthResponse("tok", null, "Bearer", 900, 1L, "email", "ROLE_DEV")`
  are unreadable and error-prone. Builder names each field.
- **"Why not setters (Setter injection)?"**
  Builder produces immutable objects. Once `.build()` is called, the object
  cannot be changed — important for response DTOs that should be read-only.

---

## 4. Strategy — `TaskStatusStrategy` (Phase 4)

**Location (planned):** `sprintly-task/strategy/`

### What it does
Encapsulates each task status transition as a separate object,
eliminating long if-else / switch chains.

### Planned Implementation
```java
// Interface
public interface TaskStatusStrategy {
    void validate(Task task);   // throws if transition is illegal
    void execute(Task task);    // applies the transition
    String getDescription();    // for logging/audit trail
}

// Concrete strategy
public class InProgressToReviewStrategy implements TaskStatusStrategy {
    public void validate(Task task) {
        if (task.getAssignee() == null)
            throw new BadRequestException("Cannot move to review: task has no assignee");
    }
    public void execute(Task task) {
        task.setStatus(TaskStatus.IN_REVIEW);
        task.setReviewRequestedAt(LocalDateTime.now());
    }
}

// Factory selects the right strategy
TaskStatusStrategyFactory
    .getStrategy(TaskStatus.IN_PROGRESS, TaskStatus.IN_REVIEW)
    .execute(task);
```

### Legal transitions
```
TODO ──────────────► IN_PROGRESS
IN_PROGRESS ────────► IN_REVIEW
IN_REVIEW ──────────► DONE
IN_REVIEW ──────────► IN_PROGRESS  (sent back for fixes)
ANY ────────────────► CANCELLED
```

### Interview Q&A
- **"Why not a switch statement?"**
  Each new status would require modifying a central switch block.
  Strategy lets each transition be added, tested, and deployed independently.
- **"How do you select the right strategy?"**
  A factory method maps `(currentStatus, newStatus)` to the correct
  strategy class. Unknown/illegal transitions throw `BadRequestException`.
