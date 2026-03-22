package com.sprintly.task.controller;

import com.sprintly.common.dto.ApiResponse;
import com.sprintly.task.dto.*;
import com.sprintly.task.service.TaskService;
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
 * Status update restrictions (Req 3):
 *   PATCH /{id}/status     → only the assignee of the task can call this
 *   PATCH /bulk-status     → only the assignee of each task can update it
 *
 * Reporter (Req 1):
 *   Every TaskDTO response includes reporterId and reporterName
 *   Reporter = the user who created the task (auto-set, never user-provided)
 */
@RestController
@RequestMapping("/api/tasks")
@Tag(
        name = "Tasks",
        description = "Task CRUD, status transitions (assignee-only), bulk status update, and assignment."
)
@SecurityRequirement(name = "BearerAuth")
public class TaskController {

    private final TaskService taskService;
    private final UserRepository userRepository;

    public TaskController(TaskService taskService, UserRepository userRepository) {
        this.taskService = taskService;
        this.userRepository = userRepository;
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Long getUserId(Principal principal) {
        return userRepository.findByEmail(principal.getName())
                .map(u -> u.getId())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }

    // ── List all ──────────────────────────────────────────────────────────────

    @Operation(
            summary = "List all tasks",
            description = "Returns all tasks. Each task includes reporterId, reporterName, assigneeId, assigneeName."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tasks retrieved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskDTO>>> list() {
        return ResponseEntity.ok(ApiResponse.success("Tasks retrieved successfully", taskService.listTasks()));
    }

    // ── List my assigned tasks ─────────────────────────────────────────────────

    @Operation(
            summary = "List tasks assigned to me",
            description = "Returns only tasks where the authenticated user is the assignee. " +
                    "Used by the CLI bulk-status command to show updatable tasks."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tasks retrieved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @GetMapping("/my-tasks")
    public ResponseEntity<ApiResponse<List<TaskDTO>>> myTasks(Principal principal) {
        Long userId = getUserId(principal);
        return ResponseEntity.ok(ApiResponse.success("Your assigned tasks", taskService.listTasksForAssignee(userId)));
    }

    // ── Get by ID ─────────────────────────────────────────────────────────────

    @Operation(summary = "Get task by ID")
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
            description = "Creates a task. The authenticated user becomes the reporter automatically. " +
                    "If assignedTo is set, the assignee receives a real-time notification."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Task created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<TaskDTO>> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Task creation payload",
                    content = @Content(
                            schema = @Schema(implementation = CreateTaskRequest.class),
                            examples = @ExampleObject(value = """
                        {
                          "title": "Fix login redirect bug",
                          "description": "OAuth2 redirect fails on Safari 16+",
                          "assignedTo": 3
                        }
                        """)
                    )
            )
            @Valid @RequestBody CreateTaskRequest req,
            Principal principal) {
        Long creatorId = getUserId(principal);
        TaskDTO dto = taskService.createTask(req, creatorId);
        return ResponseEntity.ok(ApiResponse.success("Task created successfully", dto));
    }

    // ── Update fields ─────────────────────────────────────────────────────────

    @Operation(
            summary = "Update task fields (title, description, assignedTo)",
            description = "Updates task fields. Use PATCH /{id}/status to change status."
    )
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskDTO>> update(
            @Parameter(description = "Task ID") @PathVariable Long id,
            @Valid @RequestBody UpdateTaskRequest req) {
        Optional<TaskDTO> opt = taskService.updateTask(id, req);
        return opt.map(t -> ResponseEntity.ok(ApiResponse.success("Task updated successfully", t)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ── Update status (single) ────────────────────────────────────────────────

    @Operation(
            summary = "Change task status — ASSIGNEE ONLY",
            description = """
            Changes the status of a single task.

            **Restriction:** Only the **assignee** of the task can call this endpoint.
            If you are not the assignee, you will receive a 403 error.

            Valid transitions:
            | From         | To                               |
            |--------------|----------------------------------|
            | TODO         | IN_PROGRESS, CANCELLED           |
            | IN_PROGRESS  | IN_REVIEW, CANCELLED             |
            | IN_REVIEW    | DONE, IN_PROGRESS, CANCELLED     |
            | DONE         | (terminal)                       |
            | CANCELLED    | (terminal)                       |

            When status moves to DONE, the reporter receives a notification automatically.
            """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid transition"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "You are not the assignee"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Task not found")
    })
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<TaskDTO>> updateStatus(
            @Parameter(description = "Task ID") @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(examples = @ExampleObject(value = """
                    { "status": "IN_PROGRESS" }
                    """))
            )
            @RequestBody UpdateTaskRequest req,
            Principal principal) {

        if (req.getStatus() == null || req.getStatus().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("status field is required"));
        }

        Long callerId = getUserId(principal);
        Optional<TaskDTO> opt = taskService.updateTaskStatus(id, req.getStatus(), callerId);
        return opt.map(t -> ResponseEntity.ok(ApiResponse.success("Status updated to " + t.getStatus(), t)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ── Bulk status update ────────────────────────────────────────────────────

    @Operation(
            summary = "Bulk update status for multiple tasks — ASSIGNEE ONLY",
            description = """
            Updates the status of multiple tasks in one call.

            **Restriction:** You must be the **assignee** of each task.
            Tasks where you are not the assignee are skipped and reported in `failures`.

            **Partial success:** Tasks that pass validation are updated even if others fail.
            Check `successCount`, `failureCount`, and `failures` in the response.

            When any task moves to DONE, the reporter of that task is notified automatically.

            Example request:
            ```json
            {
              "taskIds": [1, 2, 3],
              "status": "IN_PROGRESS"
            }
            ```
            """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Bulk update completed (check successCount/failureCount)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @PatchMapping("/bulk-status")
    public ResponseEntity<ApiResponse<BulkStatusResult>> bulkUpdateStatus(
            @Valid @RequestBody BulkStatusRequest req,
            Principal principal) {

        Long callerId = getUserId(principal);
        BulkStatusResult result = taskService.bulkUpdateStatus(req, callerId);

        String message = result.getSuccessCount() + " task(s) updated";
        if (result.getFailureCount() > 0) {
            message += ", " + result.getFailureCount() + " failed";
        }

        return ResponseEntity.ok(ApiResponse.success(message, result));
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Operation(summary = "Delete a task")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        boolean deleted = taskService.deleteTask(id);
        if (deleted) return ResponseEntity.noContent().build();
        return ResponseEntity.notFound().build();
    }
}