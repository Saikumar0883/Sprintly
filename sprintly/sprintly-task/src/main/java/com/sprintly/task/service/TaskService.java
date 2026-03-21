package com.sprintly.task.service;

import com.sprintly.notification.service.NotificationService;
import com.sprintly.task.dto.CreateTaskRequest;
import com.sprintly.task.dto.TaskDTO;
import com.sprintly.task.dto.UpdateTaskRequest;
import com.sprintly.task.entity.Task;
import com.sprintly.task.mapper.TaskMapper;
import com.sprintly.task.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Core business logic for task management.
 *
 * Key responsibility added (P1 fix):
 *   When a task is created with an assignee, and the assignee is different
 *   from the creator, NotificationService.notifyTaskAssigned() is called.
 *   This persists the notification to DB and pushes it via WebSocket
 *   to the assignee's connected client in real-time.
 *
 * Design Pattern: Observer (via NotificationService)
 *   TaskService (Observable) → NotificationService (Observer)
 *   The task creation event is observed and triggers a notification.
 */
@Service
public class TaskService {

    private final TaskRepository repo;
    private final NotificationService notificationService;

    /**
     * Constructor injection.
     *
     * Why inject NotificationService here and not in TaskController?
     *   Business logic belongs in the service layer. The controller
     *   only handles HTTP concerns (request parsing, response wrapping).
     *   Notification is a side-effect of task creation — a business rule.
     */
    public TaskService(TaskRepository repo, NotificationService notificationService) {
        this.repo = repo;
        this.notificationService = notificationService;
    }

    // ── List ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TaskDTO> listTasks() {
        return repo.findAll().stream()
                .map(TaskMapper::toDto)
                .collect(Collectors.toList());
    }

    // ── Get ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Optional<TaskDTO> getTask(Long id) {
        return repo.findById(id).map(TaskMapper::toDto);
    }

    // ── Create ───────────────────────────────────────────────────────────────

    /**
     * Creates a new task and triggers a notification if an assignee is set.
     *
     * Notification logic:
     *   - Only fires if assignedTo is NOT null (task has an assignee)
     *   - Only fires if assignee != creator (no point notifying yourself)
     *   - Uses NotificationService which handles DB persist + WebSocket push
     *
     * @param req       validated create task request (title, description, assignedTo)
     * @param creatorId ID of the authenticated user creating the task
     * @return TaskDTO of the newly created task
     */
    @Transactional
    public TaskDTO createTask(CreateTaskRequest req, Long creatorId) {

        // ── Step 1: Build and persist the task ───────────────────────────
        Task t = Task.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .status("TODO")
                .createdBy(creatorId)
                .assignedTo(req.getAssignedTo())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Task saved = repo.save(t);

        // ── Step 2: Notify the assignee if one was set ───────────────────
        //
        // Condition explained:
        //   req.getAssignedTo() != null      → only if there is an assignee
        //   !req.getAssignedTo().equals(creatorId) → skip if assigning to yourself
        //
        // Why skip self-assignment notification?
        //   It is valid to create a task and assign it to yourself.
        //   But notifying yourself "you have been assigned to a task you just created"
        //   is noise. Skip it.
        if (req.getAssignedTo() != null && !req.getAssignedTo().equals(creatorId)) {
            notificationService.notifyTaskAssigned(
                    saved.getId(),          // taskId  — for linking in notification
                    saved.getTitle(),       // taskTitle — shown in notification message
                    req.getAssignedTo(),    // assigneeId — who receives the notification
                    creatorId               // assignerId — who created/assigned the task
            );
        }

        return TaskMapper.toDto(saved);
    }

    // ── Update ───────────────────────────────────────────────────────────────

    /**
     * Updates an existing task's fields.
     * Only non-null fields in the request are applied (partial update).
     *
     * Note: If assignedTo changes, we could also trigger a notification here.
     * That is a Phase 2 enhancement — tracked in CLAUDE.md.
     */
    @Transactional
    public Optional<TaskDTO> updateTask(Long id, UpdateTaskRequest req) {
        Optional<Task> opt = repo.findById(id);
        if (opt.isEmpty()) return Optional.empty();

        Task task = opt.get();
        Long previousAssignee = task.getAssignedTo();

        if (req.getTitle() != null)       task.setTitle(req.getTitle());
        if (req.getDescription() != null) task.setDescription(req.getDescription());
        if (req.getStatus() != null)      task.setStatus(req.getStatus());
        if (req.getAssignedTo() != null)  task.setAssignedTo(req.getAssignedTo());
        task.setUpdatedAt(LocalDateTime.now());

        Task updated = repo.save(task);

        // Notify new assignee if assignedTo changed to a different user
        if (req.getAssignedTo() != null
                && !req.getAssignedTo().equals(previousAssignee)) {
            notificationService.notifyTaskAssigned(
                    updated.getId(),
                    updated.getTitle(),
                    req.getAssignedTo(),
                    updated.getCreatedBy()   // treat creator as the "assigner" for updates
            );
        }

        return Optional.of(TaskMapper.toDto(updated));
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    @Transactional
    public boolean deleteTask(Long id) {
        return repo.deleteById(id);
    }
}
