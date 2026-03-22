package com.sprintly.cli.command.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sprintly.cli.client.SprintlyClient;
import com.sprintly.cli.util.CliPrompt;
import com.sprintly.common.dto.ApiResponse;
import com.sprintly.task.dto.TaskDTO;
import com.sprintly.task.dto.UpdateTaskRequest;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * sprintly task status [id] [newStatus]
 *
 * Forward-only transitions. Direct jumps allowed.
 *   task status 3 DONE        -- TODO can jump straight to DONE
 *   task status 3 IN_REVIEW   -- IN_PROGRESS can jump to IN_REVIEW
 *   task status 3             -- shows all valid forward options as menu
 */
@Command(
        name = "status",
        description = "Change the status of a task (assignee only, forward moves only)",
        footer = {
                "",
                "Examples:",
                "  task status 3              (shows menu of valid options)",
                "  task status 3 DONE         (direct jump — skip steps if needed)",
                "  task status 3 IN_REVIEW",
                "  task status 3 CANCELLED",
                "",
                "Note: Backward moves are not allowed (e.g. IN_REVIEW -> TODO is blocked)."
        }
)
public class UpdateTaskStatusCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Task ID", arity = "0..1")
    private Long id;

    @Parameters(index = "1",
            description = "New status: IN_PROGRESS, IN_REVIEW, DONE, CANCELLED",
            arity = "0..1")
    private String newStatus;

    private final SprintlyClient client = new SprintlyClient();

    private static final Map<String, Integer> STATUS_RANK = Map.of(
            "TODO", 0, "IN_PROGRESS", 1, "IN_REVIEW", 2, "DONE", 3);
    private static final List<String> TERMINAL = List.of("DONE", "CANCELLED");

    private boolean isValidTransition(String current, String next) {
        if (TERMINAL.contains(current)) return false;
        if (current.equals(next)) return false;
        if ("CANCELLED".equals(next)) return true;
        Integer cr = STATUS_RANK.get(current);
        Integer nr = STATUS_RANK.get(next);
        if (cr == null || nr == null) return false;
        return nr > cr;
    }

    private List<String> getValidNext(String current) {
        if (TERMINAL.contains(current)) return List.of();
        List<String> opts = new ArrayList<>();
        Integer cr = STATUS_RANK.get(current);
        if (cr != null) {
            List.of("IN_PROGRESS", "IN_REVIEW", "DONE").forEach(s -> {
                Integer r = STATUS_RANK.get(s);
                if (r != null && r > cr) opts.add(s);
            });
        }
        opts.add("CANCELLED");
        return opts;
    }

    @Override
    public Integer call() throws Exception {

        if (!client.isLoggedIn()) {
            System.out.println();
            System.out.println("  X  Not logged in. Run: sprintly login");
            System.out.println();
            return 1;
        }

        if (id == null) {
            id = CliPrompt.promptLong("Enter task ID: ");
        }

        ApiResponse<TaskDTO> fetchResponse =
                client.get("/tasks/" + id, new TypeReference<>() {}, true);

        if (fetchResponse == null || !fetchResponse.isSuccess()) {
            System.err.println("  X  " + (fetchResponse != null
                    ? fetchResponse.getMessage() : "No response"));
            return 1;
        }

        TaskDTO task = fetchResponse.getData();
        String currentStatus = task.getStatus();

        System.out.println();
        System.out.printf("  Task    #%d: %s%n", task.getId(), task.getTitle());
        System.out.printf("  Reporter : %s%n",
                task.getReporterName() != null ? task.getReporterName() : "Unknown");
        System.out.printf("  Assignee : %s%n",
                task.getAssigneeName() != null ? task.getAssigneeName() : "Unassigned");
        System.out.printf("  Status   : %s%n", currentStatus);
        System.out.println();

        List<String> validNext = getValidNext(currentStatus);
        if (validNext.isEmpty()) {
            System.out.println("  i  Status is " + currentStatus + " — no further changes allowed.");
            System.out.println();
            return 0;
        }

        String chosenStatus;

        if (newStatus != null && !newStatus.isBlank()) {
            chosenStatus = newStatus.toUpperCase().trim();
            if (!isValidTransition(currentStatus, chosenStatus)) {
                System.err.println("  X  Cannot change: " + currentStatus + " -> " + chosenStatus);
                Integer cr = STATUS_RANK.get(currentStatus);
                Integer nr = STATUS_RANK.get(chosenStatus);
                if (cr != null && nr != null && nr < cr) {
                    System.err.println("     Backward moves are not allowed.");
                }
                System.err.println("     Valid options from " + currentStatus + ": " + validNext);
                return 1;
            }
        } else {
            System.out.println("  All valid status options from " + currentStatus + ":");
            System.out.println("  (You can jump directly — no need to go step by step)");
            System.out.println();
            for (int i = 0; i < validNext.size(); i++) {
                System.out.printf("    %d. %-14s  %s%n",
                        i + 1, validNext.get(i), getHint(validNext.get(i)));
            }
            System.out.println();
            chosenStatus = null;
            while (true) {
                String input = CliPrompt.prompt("  Enter number: ");
                try {
                    int sel = Integer.parseInt(input.trim());
                    if (sel >= 1 && sel <= validNext.size()) {
                        chosenStatus = validNext.get(sel - 1);
                        break;
                    }
                    System.err.println("  X  Enter 1-" + validNext.size());
                } catch (NumberFormatException e) {
                    System.err.println("  X  Invalid. Enter a number.");
                }
            }
        }

        UpdateTaskRequest request = new UpdateTaskRequest();
        request.setStatus(chosenStatus);

        ApiResponse<TaskDTO> response =
                client.patch("/tasks/" + id + "/status", request, new TypeReference<>() {}, true);

        System.out.println();
        if (response != null && response.isSuccess()) {
            TaskDTO updated = response.getData();
            System.out.println("  OK  Status updated!");
            System.out.printf("     %s  ->  %s%n", currentStatus, updated.getStatus());
            if ("DONE".equals(updated.getStatus()) && updated.getReporterName() != null) {
                System.out.printf("     Reporter %s has been notified.%n", updated.getReporterName());
            }
        } else {
            String msg = response != null ? response.getMessage() : "No response";
            System.err.println("  X  " + msg);
            if (msg != null && msg.contains("assignee")) {
                System.err.println("     Only the assignee can update task status.");
            }
            return 1;
        }
        System.out.println();
        return 0;
    }

    private String getHint(String s) {
        return switch (s) {
            case "IN_PROGRESS" -> "<- start working";
            case "IN_REVIEW"   -> "<- ready for review";
            case "DONE"        -> "<- complete (reporter notified)";
            case "CANCELLED"   -> "<- cancel";
            default -> "";
        };
    }
}