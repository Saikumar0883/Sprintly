package com.sprintly.cli.command.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sprintly.cli.client.SprintlyClient;
import com.sprintly.cli.util.CliPrompt;
import com.sprintly.common.dto.ApiResponse;
import com.sprintly.task.dto.BulkStatusRequest;
import com.sprintly.task.dto.BulkStatusResult;
import com.sprintly.task.dto.TaskDTO;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(
        name = "bulk-status",
        description = "Update the status of multiple tasks you are assigned to at once",
        footer = {
                "",
                "Examples:",
                "  task bulk-status",
                "  task bulk-status --status IN_PROGRESS"
        }
)
public class BulkStatusCommand implements Callable<Integer> {

    @Option(names = {"-s", "--status"},
            description = "New status: IN_PROGRESS, IN_REVIEW, DONE, CANCELLED")
    private String status;

    private final SprintlyClient client = new SprintlyClient();

    private static final Map<String, List<String>> VALID_TRANSITIONS = Map.of(
            "TODO",        List.of("IN_PROGRESS", "CANCELLED"),
            "IN_PROGRESS", List.of("IN_REVIEW", "CANCELLED"),
            "IN_REVIEW",   List.of("DONE", "IN_PROGRESS", "CANCELLED"),
            "DONE",        List.of(),
            "CANCELLED",   List.of()
    );

    @Override
    public Integer call() throws Exception {

        if (!client.isLoggedIn()) {
            System.out.println();
            System.out.println("  X  You are not logged in. Run: sprintly login");
            System.out.println();
            return 1;
        }

        System.out.println();
        System.out.println("  Fetching your assigned tasks...");

        ApiResponse<List<TaskDTO>> myTasksResponse =
                client.get("/tasks/my-tasks", new TypeReference<>() {}, true);

        if (myTasksResponse == null || !myTasksResponse.isSuccess()) {
            System.err.println("  X  " + (myTasksResponse != null
                    ? myTasksResponse.getMessage() : "No response"));
            return 1;
        }

        List<TaskDTO> myTasks = myTasksResponse.getData();

        if (myTasks == null || myTasks.isEmpty()) {
            System.out.println("  i  You have no tasks assigned to you.");
            System.out.println();
            return 0;
        }

        // Show TASK ID prominently - user selects by ID, not row number
        System.out.println();
        System.out.printf("  %-8s  %-30s  %-14s%n", "TASK ID", "Title", "Status");
        System.out.println("  " + "-".repeat(58));
        for (TaskDTO t : myTasks) {
            System.out.printf("  %-8d  %-30s  %-14s%n",
                    t.getId(),
                    truncate(t.getTitle(), 30),
                    t.getStatus());
        }
        System.out.println("  " + "-".repeat(58));
        System.out.println();

        // Valid IDs are the actual task IDs in the list
        List<Long> validIds = myTasks.stream().map(TaskDTO::getId).toList();

        // Build example string from actual IDs
        String exampleIds = validIds.size() >= 2
                ? validIds.get(0) + "," + validIds.get(1)
                : String.valueOf(validIds.get(0));

        System.out.println("  Enter the TASK IDs to update (the numbers in the TASK ID column).");
        System.out.println("  Separate with commas or spaces, or type 'all'.");
        System.out.println("  Example: " + exampleIds + "   or   all");
        System.out.println();

        List<Long> selectedIds = new ArrayList<>();
        while (true) {
            String input = CliPrompt.prompt("  Task IDs: ");
            if (input == null || input.isBlank()) {
                System.err.println("  X  Please enter task IDs or 'all'.");
                continue;
            }
            input = input.trim();

            if (input.equalsIgnoreCase("all")) {
                selectedIds.addAll(validIds);
                System.out.println("  -> All " + myTasks.size() + " task(s) selected.");
                break;
            }

            // Support comma AND space as separators
            String[] parts = input.split("[,\\s]+");
            boolean valid = true;
            List<Long> ids = new ArrayList<>();

            for (String part : parts) {
                part = part.trim();
                if (part.isEmpty()) continue;

                long taskId;
                try {
                    taskId = Long.parseLong(part);
                } catch (NumberFormatException e) {
                    System.err.println("  X  '" + part + "' is not a valid task ID.");
                    valid = false;
                    break;
                }

                if (!validIds.contains(taskId)) {
                    System.err.println("  X  Task ID " + taskId + " is not in your assigned tasks.");
                    System.err.println("     Your assigned task IDs: " + validIds);
                    valid = false;
                    break;
                }

                if (!ids.contains(taskId)) ids.add(taskId);
            }

            if (valid && !ids.isEmpty()) {
                selectedIds.addAll(ids);
                System.out.println("  -> " + selectedIds.size() + " task(s) selected: " + selectedIds);
                break;
            }
        }

        // Select status
        if (status == null) {
            System.out.println();
            System.out.println("  Select new status:");
            List<String> opts = List.of("IN_PROGRESS", "IN_REVIEW", "DONE", "CANCELLED");
            for (int i = 0; i < opts.size(); i++) {
                System.out.printf("    %d. %-14s  %s%n", i + 1, opts.get(i), getHint(opts.get(i)));
            }
            System.out.println();
            while (true) {
                String input = CliPrompt.prompt("  Enter number: ");
                try {
                    int sel = Integer.parseInt(input.trim());
                    if (sel >= 1 && sel <= opts.size()) { status = opts.get(sel - 1); break; }
                    System.err.println("  X  Enter 1-" + opts.size());
                } catch (NumberFormatException e) {
                    System.err.println("  X  Invalid. Enter a number.");
                }
            }
        } else {
            status = status.toUpperCase().trim();
        }

        // Confirm
        System.out.println();
        System.out.printf("  About to set %d task(s) -> %s%n", selectedIds.size(), status);
        System.out.println("  Task IDs: " + selectedIds);
        String confirm = CliPrompt.prompt("  Confirm? [Y/n]: ");
        if (confirm != null && confirm.trim().equalsIgnoreCase("n")) {
            System.out.println("  Cancelled.");
            System.out.println();
            return 0;
        }

        // Send
        BulkStatusRequest request = new BulkStatusRequest();
        request.setTaskIds(selectedIds);
        request.setStatus(status);

        ApiResponse<BulkStatusResult> response =
                client.patch("/tasks/bulk-status", request, new TypeReference<>() {}, true);

        System.out.println();
        if (response != null && response.isSuccess()) {
            BulkStatusResult result = response.getData();
            System.out.printf("  OK  %d task(s) updated to %s%n", result.getSuccessCount(), status);
            if (result.getUpdatedTasks() != null) {
                for (TaskDTO t : result.getUpdatedTasks()) {
                    String extra = "DONE".equals(t.getStatus()) && t.getReporterName() != null
                            ? "  (reporter " + t.getReporterName() + " notified)" : "";
                    System.out.printf("       #%d - %s%s%n", t.getId(), t.getTitle(), extra);
                }
            }
            if (result.getFailureCount() > 0) {
                System.out.println();
                System.out.printf("  WARN  %d task(s) failed:%n", result.getFailureCount());
                if (result.getFailures() != null) {
                    for (BulkStatusResult.FailureDetail f : result.getFailures()) {
                        System.out.printf("       X  #%d: %s%n", f.getTaskId(), f.getReason());
                    }
                }
            }
        } else {
            System.err.println("  X  " + (response != null ? response.getMessage() : "No response"));
            return 1;
        }
        System.out.println();
        return 0;
    }

    private String getHint(String s) {
        return switch (s) {
            case "IN_PROGRESS" -> "<- start working";
            case "IN_REVIEW"   -> "<- ready for review";
            case "DONE"        -> "<- complete (reporters notified)";
            case "CANCELLED"   -> "<- cancel tasks";
            default -> "";
        };
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 3) + "..." : s;
    }
}