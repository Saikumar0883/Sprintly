package com.sprintly.cli.command.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sprintly.cli.client.SprintlyClient;
import com.sprintly.cli.util.CliPrompt;
import com.sprintly.common.dto.ApiResponse;
import com.sprintly.task.dto.TaskDTO;
import com.sprintly.task.dto.UpdateTaskRequest;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * CLI command: sprintly task status <id>
 *
 * Allows a user to change the status of a task they are assigned to.
 *
 * Valid statuses and transitions:
 *   TODO        → IN_PROGRESS
 *   IN_PROGRESS → IN_REVIEW
 *   IN_REVIEW   → DONE
 *   IN_REVIEW   → IN_PROGRESS  (sent back for rework)
 *   ANY         → CANCELLED
 *
 * Usage:
 *   sprintly task status 5                  ← prompts for new status
 *   sprintly task status 5 --status DONE    ← sets directly
 *
 * Flow:
 *   1. Login guard
 *   2. Fetch current task to show what it is now
 *   3. Show valid next statuses based on current status
 *   4. User selects from numbered list
 *   5. PATCH /api/tasks/{id}/status
 *   6. Show updated task
 */
@Command(name = "status", description = "Change the status of a task (TODO → IN_PROGRESS → IN_REVIEW → DONE)")
public class UpdateTaskStatusCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Task ID", arity = "0..1")
    private Long id;

    @Option(names = {"-s", "--status"},
            description = "New status: TODO, IN_PROGRESS, IN_REVIEW, DONE, CANCELLED")
    private String status;

    private final SprintlyClient client = new SprintlyClient();

    // Valid transitions — what statuses are allowed from each current status
    private static final java.util.Map<String, List<String>> VALID_TRANSITIONS =
            java.util.Map.of(
                    "TODO",        List.of("IN_PROGRESS", "CANCELLED"),
                    "IN_PROGRESS", List.of("IN_REVIEW", "CANCELLED"),
                    "IN_REVIEW",   List.of("DONE", "IN_PROGRESS", "CANCELLED"),
                    "DONE",        List.of(),          // terminal — no further transitions
                    "CANCELLED",   List.of()            // terminal — no further transitions
            );

    @Override
    public Integer call() throws Exception {

        // ── Login guard ───────────────────────────────────────────────
        if (!client.isLoggedIn()) {
            System.out.println();
            System.out.println("  ✖  You are not logged in.");
            System.out.println("     Run:  sprintly login");
            System.out.println();
            return 1;
        }

        // ── Step 1: Get task ID ───────────────────────────────────────
        if (id == null) {
            id = CliPrompt.promptLong("Enter task ID: ");
        }

        // ── Step 2: Fetch current task ────────────────────────────────
        ApiResponse<TaskDTO> fetchResponse =
                client.get("/tasks/" + id, new TypeReference<>() {}, true);

        if (!fetchResponse.isSuccess()) {
            System.err.println("  ✖  Task not found: " + fetchResponse.getMessage());
            return 1;
        }

        TaskDTO task = fetchResponse.getData();
        String currentStatus = task.getStatus();

        System.out.println();
        System.out.printf("  Task #%d: %s%n", task.getId(), task.getTitle());
        System.out.printf("  Current status: %s%n", currentStatus);
        System.out.println();

        // ── Step 3: Check if task is in a terminal state ──────────────
        List<String> validNext = VALID_TRANSITIONS.getOrDefault(currentStatus, List.of());
        if (validNext.isEmpty()) {
            System.out.println("  ℹ  This task is " + currentStatus + " — no further status changes allowed.");
            System.out.println();
            return 0;
        }

        // ── Step 4: Select new status ─────────────────────────────────
        if (status == null) {
            // Show numbered list of valid next statuses
            System.out.println("  Select new status:");
            for (int i = 0; i < validNext.size(); i++) {
                String next = validNext.get(i);
                System.out.printf("    %d. %s%s%n", i + 1, next, getStatusHint(next));
            }
            System.out.println();

            while (true) {
                String input = CliPrompt.prompt("  Enter number: ");
                if (input == null || input.isBlank()) {
                    System.err.println("  ✖  Please enter a number.");
                    continue;
                }
                try {
                    int sel = Integer.parseInt(input.trim());
                    if (sel >= 1 && sel <= validNext.size()) {
                        status = validNext.get(sel - 1);
                        break;
                    } else {
                        System.err.println("  ✖  Enter a number between 1 and " + validNext.size());
                    }
                } catch (NumberFormatException e) {
                    System.err.println("  ✖  Invalid input. Enter a number.");
                }
            }
        } else {
            // --status flag was passed — validate it
            status = status.toUpperCase().trim();
            if (!validNext.contains(status)) {
                System.err.println("  ✖  Invalid transition: " + currentStatus + " → " + status);
                System.err.println("     Allowed from " + currentStatus + ": " + validNext);
                return 1;
            }
        }

        // ── Step 5: Send PATCH request ────────────────────────────────
        UpdateTaskRequest request = new UpdateTaskRequest();
        request.setStatus(status);

        ApiResponse<TaskDTO> updateResponse =
                client.patch("/tasks/" + id + "/status", request, new TypeReference<>() {}, true);

        // ── Step 6: Show result ───────────────────────────────────────
        if (updateResponse != null && updateResponse.isSuccess()) {
            TaskDTO updated = updateResponse.getData();
            System.out.println();
            System.out.println("  ✔  Status updated successfully!");
            System.out.printf("     Task  : %s%n", updated.getTitle());
            System.out.printf("     Status: %s  →  %s%n", currentStatus, updated.getStatus());
            System.out.println();
            return 0;
        } else {
            System.err.println("  ✖  Failed to update status: "
                    + (updateResponse != null ? updateResponse.getMessage() : "No response"));
            return 1;
        }
    }

    /**
     * Returns a short contextual hint shown next to each status option.
     * Helps users understand what each transition means in plain English.
     */
    private String getStatusHint(String s) {
        return switch (s) {
            case "IN_PROGRESS" -> "  ← start working on it";
            case "IN_REVIEW"   -> "  ← ready for review";
            case "DONE"        -> "  ← mark as complete ✓";
            case "CANCELLED"   -> "  ← cancel this task";
            default            -> "";
        };
    }
}