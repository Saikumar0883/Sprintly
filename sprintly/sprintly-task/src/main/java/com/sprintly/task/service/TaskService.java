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
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Core business logic for task management.
 *
 * Key responsibilities:
 *   - createTask()        → save + notify assignee via NotificationService
 *   - updateTask()        → partial field update + notify on assignee change
 *   - updateTaskStatus()  → validate transition + save + return updated task
 *   - listTasks()         → fetch all with JOINed names
 *   - getTask()           → fetch single with JOINed names
 *   - deleteTask()        → delete by ID
 */
@Service
public class TaskService {

    private final TaskRepository repo;
    private final NotificationService notificationService;

    // Valid status transitions — enforced at service layer
    // Same rules shown in CLI UpdateTaskStatusCommand and Swagger docs
    private static final Map<String, List<String>> VALID_TRANSITIONS = Map.of(
            "TODO",        List.of("IN_PROGRESS", "CANCELLED"),
            "IN_PROGRESS", List.of("IN_REVIEW", "CANCELLED"),
            "IN_REVIEW",   List.of("DONE", "IN_PROGRESS", "CANCELLED"),
            "DONE",        List.of(),
            "CANCELLED",   List.of()
    );

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
     * Creates a task and sends a notification to the assignee if:
     *   - assignedTo is not null (task has an assignee)
     *   - assignedTo != creatorId (no self-notification)
     */
    @Transactional
    public TaskDTO createTask(CreateTaskRequest req, Long creatorId) {
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

        if (req.getAssignedTo() != null && !req.getAssignedTo().equals(creatorId)) {
            notificationService.notifyTaskAssigned(
                    saved.getId(),
                    saved.getTitle(),
                    req.getAssignedTo(),
                    creatorId
            );
        }

        return TaskMapper.toDto(saved);
    }

    // ── Update (full fields) ─────────────────────────────────────────────────

    /**
     * Partial field update — only non-null fields in the request are applied.
     * Also sends notification if assignedTo changes to a different user.
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

        // Notify new assignee if assignedTo changed
        if (req.getAssignedTo() != null
                && !req.getAssignedTo().equals(previousAssignee)) {
            notificationService.notifyTaskAssigned(
                    updated.getId(),
                    updated.getTitle(),
                    req.getAssignedTo(),
                    updated.getCreatedBy()
            );
        }

        return Optional.of(TaskMapper.toDto(updated));
    }

    // ── Update Status (PATCH) ────────────────────────────────────────────────

    /**
     * Changes only the status of a task.
     * Validates the transition is legal before saving.
     *
     * Called by:
     *   PATCH /api/tasks/{id}/status  (REST — Swagger)
     *   sprintly task status <id>     (CLI — UpdateTaskStatusCommand)
     *
     * @param id        task ID
     * @param newStatus requested new status string
     * @return updated TaskDTO, or empty if task not found
     * @throws IllegalArgumentException if the transition is not allowed
     */
    @Transactional
    public Optional<TaskDTO> updateTaskStatus(Long id, String newStatus) {
        Optional<Task> opt = repo.findById(id);
        if (opt.isEmpty()) return Optional.empty();

        Task task = opt.get();
        String currentStatus = task.getStatus();
        String normalizedNew = newStatus.toUpperCase().trim();

        // Validate transition
        List<String> allowed = VALID_TRANSITIONS.getOrDefault(currentStatus, List.of());
        if (!allowed.contains(normalizedNew)) {
            throw new IllegalArgumentException(
                    "Invalid status transition: " + currentStatus + " → " + normalizedNew
                            + ". Allowed from " + currentStatus + ": " + allowed
            );
        }

        task.setStatus(normalizedNew);
        task.setUpdatedAt(LocalDateTime.now());
        Task updated = repo.save(task);

        return Optional.of(TaskMapper.toDto(updated));
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    @Transactional
    public boolean deleteTask(Long id) {
        return repo.deleteById(id);
    }
}