package com.sprintly.task.service;

import com.sprintly.notification.service.NotificationService;
import com.sprintly.task.dto.BulkStatusRequest;
import com.sprintly.task.dto.BulkStatusResult;
import com.sprintly.task.dto.CreateTaskRequest;
import com.sprintly.task.dto.TaskDTO;
import com.sprintly.task.dto.UpdateTaskRequest;
import com.sprintly.task.entity.Task;
import com.sprintly.task.mapper.TaskMapper;
import com.sprintly.task.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Task business logic.
 *
 * Transition rules (updated from strict step-by-step to forward-only):
 *
 *   Rank: TODO(0) → IN_PROGRESS(1) → IN_REVIEW(2) → DONE(3)
 *
 *   ALLOWED:  any move where rank(new) > rank(current)
 *     TODO        → IN_PROGRESS  OK
 *     TODO        → IN_REVIEW    OK  (skip)
 *     TODO        → DONE         OK  (skip directly)
 *     IN_PROGRESS → DONE         OK  (skip IN_REVIEW)
 *     IN_REVIEW   → DONE         OK
 *     ANY non-terminal → CANCELLED  OK
 *
 *   BLOCKED: backward moves and moves from terminal states
 *     IN_REVIEW   → IN_PROGRESS  BLOCKED (downgrade)
 *     DONE        → anything     BLOCKED (terminal)
 *     CANCELLED   → anything     BLOCKED (terminal)
 */
@Service
public class TaskService {

    private final TaskRepository repo;
    private final NotificationService notificationService;

    private static final Map<String, Integer> STATUS_RANK = Map.of(
            "TODO",        0,
            "IN_PROGRESS", 1,
            "IN_REVIEW",   2,
            "DONE",        3
    );

    private static final List<String> TERMINAL_STATES = List.of("DONE", "CANCELLED");

    public TaskService(TaskRepository repo, NotificationService notificationService) {
        this.repo = repo;
        this.notificationService = notificationService;
    }

    // ── Transition helpers ────────────────────────────────────────────────────

    public boolean isValidTransition(TaskStatus current, TaskStatus next) {
        if (current == null || next == null) return false;
        if (TERMINAL_STATES.contains(current)) return false;
        if (current == next) return false;
        if (next == TaskStatus.CANCELLED) return true;
        Integer cr = STATUS_RANK.get(current);
        Integer nr = STATUS_RANK.get(next);
        if (cr == null || nr == null) return false;
        return nr > cr;
    }

    public List<String> getValidNextStatuses(String current) {
        if (current == null || TERMINAL_STATES.contains(current.toUpperCase())) return List.of();
        List<String> opts = new ArrayList<>();
        Integer cr = STATUS_RANK.get(current.toUpperCase());
        if (cr != null) {
            List.of("IN_PROGRESS", "IN_REVIEW", "DONE").forEach(s -> {
                Integer r = STATUS_RANK.get(s);
                if (r != null && r > cr) opts.add(s);
            });
        }
        opts.add("CANCELLED");
        return opts;
    }

    private String transitionError(String current, String next) {
        if (TERMINAL_STATES.contains(current.toUpperCase()))
            return "Task is already " + current + " — no further changes allowed.";
        if (current.equalsIgnoreCase(next))
            return "Task is already " + current + ".";
        Integer cr = STATUS_RANK.get(current.toUpperCase());
        Integer nr = STATUS_RANK.get(next.toUpperCase());
        if (cr != null && nr != null && nr < cr)
            return "Cannot downgrade from " + current + " to " + next
                    + ". Backward moves are not allowed. "
                    + "Valid forward options: " + getValidNextStatuses(current);
        return "Invalid transition: " + current + " -> " + next
                + ". Valid options: " + getValidNextStatuses(current);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TaskDTO> listTasks() {
        return repo.findAll().stream().map(TaskMapper::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TaskDTO> listTasksForAssignee(Long assigneeId) {
        return repo.findByAssigneeId(assigneeId).stream()
                .map(TaskMapper::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<TaskDTO> getTask(Long id) {
        return repo.findById(id).map(TaskMapper::toDto);
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public TaskDTO createTask(CreateTaskRequest req, Long creatorId) {
        Task t = Task.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .status("TODO")
                .createdBy(creatorId)
                .reporterId(creatorId)
                .assignedTo(req.getAssignedTo())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        Task saved = repo.save(t);
        if (req.getAssignedTo() != null && !req.getAssignedTo().equals(creatorId)) {
            notificationService.notifyTaskAssigned(
                    saved.getId(), saved.getTitle(), req.getAssignedTo(), creatorId);
        }
        return TaskMapper.toDto(saved);
    }

    // ── Update fields ─────────────────────────────────────────────────────────

    @Transactional
    public Optional<TaskDTO> updateTask(Long id, UpdateTaskRequest req) {
        Optional<Task> opt = repo.findById(id);
        if (opt.isEmpty()) return Optional.empty();
        Task task = opt.get();
        Long prev = task.getAssignedTo();
        if (req.getTitle() != null)       task.setTitle(req.getTitle());
        if (req.getDescription() != null) task.setDescription(req.getDescription());
        if (req.getAssignedTo() != null)  task.setAssignedTo(req.getAssignedTo());
        task.setUpdatedAt(LocalDateTime.now());
        Task updated = repo.save(task);
        if (req.getAssignedTo() != null && !req.getAssignedTo().equals(prev)) {
            notificationService.notifyTaskAssigned(
                    updated.getId(), updated.getTitle(),
                    req.getAssignedTo(), updated.getCreatedBy());
        }
        return Optional.of(TaskMapper.toDto(updated));
    }

    // ── Update status (single) ────────────────────────────────────────────────

    @Transactional
    public Optional<TaskDTO> updateTaskStatus(Long id, String newStatus, Long callerId) {
        Optional<Task> opt = repo.findById(id);
        if (opt.isEmpty()) return Optional.empty();
        Task task = opt.get();

        if (task.getAssignedTo() == null || !task.getAssignedTo().equals(callerId)) {
            throw new IllegalStateException(
                    "Only the assignee can update the status of task #" + id
                            + ". Assigned to: "
                            + (task.getAssignedTo() != null ? "user #" + task.getAssignedTo() : "nobody"));
        }

        String current = task.getStatus();
        String next    = newStatus.toUpperCase().trim();

        if (!isValidTransition(current, next)) {
            throw new IllegalArgumentException(transitionError(current, next));
        }

        task.setStatus(next);
        task.setUpdatedAt(LocalDateTime.now());
        Task updated = repo.save(task);

        if ("DONE".equals(next)) {
            Long reporterId = task.getCreatedBy();
            if (reporterId != null && !reporterId.equals(callerId)) {
                notificationService.notifyTaskDone(
                        updated.getId(), updated.getTitle(), callerId, reporterId);
            }
        }

        return Optional.of(TaskMapper.toDto(updated));
    }

    // ── Bulk status update ────────────────────────────────────────────────────

    @Transactional
    public BulkStatusResult bulkUpdateStatus(BulkStatusRequest req, Long callerId) {
        String next = req.getStatus().toUpperCase().trim();
        List<TaskDTO> updated   = new ArrayList<>();
        List<BulkStatusResult.FailureDetail> failures = new ArrayList<>();

        for (Long taskId : req.getTaskIds()) {
            try {
                Optional<Task> opt = repo.findById(taskId);
                if (opt.isEmpty()) {
                    failures.add(fail(taskId, "Task #" + taskId + " not found")); continue;
                }
                Task task = opt.get();

                if (task.getAssignedTo() == null || !task.getAssignedTo().equals(callerId)) {
                    failures.add(fail(taskId, "You are not the assignee of task #" + taskId
                            + " (\"" + task.getTitle() + "\")")); continue;
                }

                if (!isValidTransition(task.getStatus(), next)) {
                    failures.add(fail(taskId, "\"" + task.getTitle() + "\": "
                            + transitionError(task.getStatus(), next))); continue;
                }

                task.setStatus(next);
                task.setUpdatedAt(LocalDateTime.now());
                Task saved = repo.save(task);
                updated.add(TaskMapper.toDto(saved));

                if ("DONE".equals(next)) {
                    Long reporterId = task.getCreatedBy();
                    if (reporterId != null && !reporterId.equals(callerId)) {
                        notificationService.notifyTaskDone(
                                saved.getId(), saved.getTitle(), callerId, reporterId);
                    }
                }
            } catch (Exception e) {
                failures.add(fail(taskId, "Error: " + e.getMessage()));
            }
        }

        return BulkStatusResult.builder()
                .successCount(updated.size())
                .failureCount(failures.size())
                .updatedTasks(updated)
                .failures(failures)
                .build();
    }

    private BulkStatusResult.FailureDetail fail(Long id, String reason) {
        return BulkStatusResult.FailureDetail.builder().taskId(id).reason(reason).build();
    }

    @Transactional
    public boolean deleteTask(Long id) {
        return repo.deleteById(id);
    }
}