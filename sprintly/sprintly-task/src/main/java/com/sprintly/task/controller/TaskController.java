package com.sprintly.task.controller;

import com.sprintly.task.dto.CreateTaskRequest;
import com.sprintly.task.dto.TaskDTO;
import com.sprintly.task.dto.UpdateTaskRequest;
import com.sprintly.task.service.TaskService;
import com.sprintly.common.dto.ApiResponse;
import com.sprintly.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

/**
 * REST controller for task management.
 *
 * All endpoints require JWT authentication (Bearer token).
 * Use POST /api/auth/login to get a token, then click Authorize in Swagger UI.
 *
 * Status transition rules:
 *   TODO → IN_PROGRESS → IN_REVIEW → DONE
 *   IN_REVIEW → IN_PROGRESS  (sent back for rework)
 *   ANY → CANCELLED
 */
@RestController
@RequestMapping("/api/tasks")
@Tag(
        name = "Tasks",
        description = "Task CRUD, status transitions, and assignment. " +
                "All endpoints require a valid JWT Bearer token."
)
@SecurityRequirement(name = "BearerAuth")
public class TaskController {

    private final TaskService taskService;
    private final UserRepository userRepository;

    public TaskController(TaskService taskService, UserRepository userRepository) {
        this.taskService = taskService;
        this.userRepository = userRepository;
    }

    // ── List ─────────────────────────────────────────────────────────────────

    @Operation(
            summary = "List all tasks",
            description = "Returns all tasks in the system. Each task includes the assignee name " +
                    "and creator name resolved from a database JOIN — no extra calls needed."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Tasks retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Not authenticated")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskDTO>>> list() {
        List<TaskDTO> tasks = taskService.listTasks();
        return ResponseEntity.ok(ApiResponse.success("Tasks retrieved successfully", tasks));
    }

    // ── Get by ID ─────────────────────────────────────────────────────────────

    @Operation(
            summary = "Get task by ID",
            description = "Returns full details of a single task including assignee name."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Task found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Task not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Not authenticated")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskDTO>> get(
            @Parameter(description = "Task ID", example = "1") @PathVariable Long id) {
        Optional<TaskDTO> opt = taskService.getTask(id);
        return opt.map(t -> ResponseEntity.ok(ApiResponse.success("Task retrieved successfully", t)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Operation(
            summary = "Create a new task",
            description = "Creates a task and assigns it to the specified user. " +
                    "If assignedTo is provided and the assignee is different from the creator, " +
                    "a real-time notification is sent to the assignee via WebSocket."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Task created successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "Validation failed (title is required)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Not authenticated")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<TaskDTO>> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Task creation payload",
                    content = @Content(
                            schema = @Schema(implementation = CreateTaskRequest.class),
                            examples = @ExampleObject(
                                    value = """
                            {
                              "title": "Fix login redirect bug",
                              "description": "OAuth2 redirect fails on Safari 16+",
                              "assignedTo": 3
                            }
                            """
                            )
                    )
            )
            @Valid @RequestBody CreateTaskRequest req,
            Principal principal) {

        String userEmail = principal.getName();
        Long creatorId = userRepository.findByEmail(userEmail)
                .map(user -> user.getId())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

        TaskDTO dto = taskService.createTask(req, creatorId);
        return ResponseEntity.ok(ApiResponse.success("Task created successfully", dto));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Operation(
            summary = "Update task fields",
            description = "Full update of task title, description, assignedTo. " +
                    "Use PATCH /{id}/status to change only the status."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Task updated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Task not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Not authenticated")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskDTO>> update(
            @Parameter(description = "Task ID", example = "1") @PathVariable Long id,
            @Valid @RequestBody UpdateTaskRequest req) {
        Optional<TaskDTO> opt = taskService.updateTask(id, req);
        return opt.map(t -> ResponseEntity.ok(ApiResponse.success("Task updated successfully", t)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ── Update Status ─────────────────────────────────────────────────────────

    @Operation(
            summary = "Change task status",
            description = """
            Changes only the status of a task. Valid transitions:

            | From         | To                                  |
            |--------------|-------------------------------------|
            | TODO         | IN_PROGRESS, CANCELLED              |
            | IN_PROGRESS  | IN_REVIEW, CANCELLED                |
            | IN_REVIEW    | DONE, IN_PROGRESS, CANCELLED        |
            | DONE         | (terminal — no further changes)     |
            | CANCELLED    | (terminal — no further changes)     |

            This is a PATCH (partial update) — only send the `status` field.
            The CLI uses this endpoint for the `sprintly task status <id>` command.
            """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Status updated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "Invalid status transition"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Task not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Not authenticated")
    })
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<TaskDTO>> updateStatus(
            @Parameter(description = "Task ID", example = "1") @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "New status value",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                            {
                              "status": "IN_PROGRESS"
                            }
                            """
                            )
                    )
            )
            @RequestBody UpdateTaskRequest req) {

        // Only the status field is used from the request
        if (req.getStatus() == null || req.getStatus().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("status field is required"));
        }

        Optional<TaskDTO> opt = taskService.updateTaskStatus(id, req.getStatus());
        return opt.map(t -> ResponseEntity.ok(ApiResponse.success(
                        "Status updated to " + t.getStatus(), t)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Operation(
            summary = "Delete a task",
            description = "Permanently deletes a task by ID."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204", description = "Task deleted successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Task not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Not authenticated")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Task ID", example = "1") @PathVariable Long id) {
        boolean deleted = taskService.deleteTask(id);
        if (deleted) return ResponseEntity.noContent().build();
        return ResponseEntity.notFound().build();
    }
}