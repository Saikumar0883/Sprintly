package com.sprintly.cli.command.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sprintly.cli.client.SprintlyClient;
import com.sprintly.cli.config.CliConfig;
import com.sprintly.cli.util.CliPrompt;
import com.sprintly.common.dto.ApiResponse;
import com.sprintly.common.dto.UserDTO;
import com.sprintly.task.dto.CreateTaskRequest;
import com.sprintly.task.dto.TaskDTO;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * CLI command: sprintly task create
 *
 * Fixes in this version:
 *   1. Login guard runs BEFORE any prompts — user sees login message instantly
 *   2. Shows "already logged in as X" using saved name from CliConfig
 *   3. SprintlyClient.get() now reads body as String first —
 *      no more MismatchedInputException on empty response
 *   4. Null + empty checks on user list response
 *   5. "Unassigned" as last option in user list
 */
@Command(name = "create", description = "Create a new task")
public class CreateTaskCommand implements Callable<Integer> {

    @Option(names = {"-t", "--title"}, description = "Task title")
    private String title;

    @Option(names = {"-d", "--description"}, description = "Task description")
    private String description;

    @Option(names = {"-a", "--assignee"}, description = "Assignee user ID (skips interactive selection)")
    private Long assignedTo;

    private final SprintlyClient client = new SprintlyClient();

    @Override
    public Integer call() throws Exception {

        // ── GUARD: Check login BEFORE asking for title/description ────────
        //
        // This runs FIRST — before any CliPrompt.prompt() calls.
        // If not logged in, we exit immediately with a clear message.
        // The user does NOT have to type a title only to be rejected after.
        //
        // isLoggedIn() reads ~/.sprintly-cli.json and checks for a token.
        // It does NOT make a network call — purely local file check.
        if (!client.isLoggedIn()) {
            System.out.println();
            System.out.println("  ✖  You are not logged in.");
            System.out.println("     Run:  sprintly login");
            System.out.println();
            return 1;
        }

        // Show who is currently logged in (name if available, else email)
        CliConfig session = CliConfig.load();
        String loggedInAs = (session != null && session.getName() != null)
                ? session.getName()
                : (session != null ? session.getEmail() : "unknown");
        System.out.println("  Creating task as: " + loggedInAs);
        System.out.println();
        // ─────────────────────────────────────────────────────────────────

        // ── Step 1: Title ─────────────────────────────────────────────────
        if (title == null) {
            title = CliPrompt.prompt("Enter task title: ");
            if (title == null || title.isBlank()) {
                System.err.println("  ✖  Title cannot be empty.");
                return 1;
            }
        }

        // ── Step 2: Description ───────────────────────────────────────────
        if (description == null) {
            description = CliPrompt.prompt("Enter task description (optional, press Enter to skip): ");
        }

        // ── Step 3: Assignee selection ────────────────────────────────────
        if (assignedTo == null) {
            System.out.println();
            System.out.println("Fetching available users...");

            ApiResponse<List<UserDTO>> usersResponse =
                    client.get("/users", new TypeReference<>() {}, true);

            // usersResponse will never be null now (SprintlyClient handles empty bodies)
            // but the isSuccess() flag tells us if it worked
            if (!usersResponse.isSuccess()) {
                System.err.println("  ✖  Could not fetch users: " + usersResponse.getMessage());
                System.err.println("     Task creation cancelled.");
                return 1;
            }

            List<UserDTO> users = usersResponse.getData();

            if (users == null || users.isEmpty()) {
                System.out.println("  ⚠  No other users found. Task will be Unassigned.");
                assignedTo = null;

            } else {
                // Print numbered list
                System.out.println();
                for (int i = 0; i < users.size(); i++) {
                    UserDTO u = users.get(i);
                    System.out.printf("  %d. %-25s (%s)%n", i + 1, u.getName(), u.getEmail());
                }
                System.out.printf("  %d. Unassigned%n", users.size() + 1);
                System.out.println();

                // Keep asking until valid number entered
                while (true) {
                    String input = CliPrompt.prompt("Select assignee (enter number): ");
                    if (input == null || input.isBlank()) {
                        System.err.println("  ✖  Please enter a number.");
                        continue;
                    }
                    try {
                        int sel = Integer.parseInt(input.trim());
                        if (sel == users.size() + 1) {
                            assignedTo = null;
                            System.out.println("  → Task will be Unassigned.");
                            break;
                        } else if (sel >= 1 && sel <= users.size()) {
                            UserDTO picked = users.get(sel - 1);
                            assignedTo = picked.getId();
                            System.out.println("  → Assigned to: " + picked.getName());
                            break;
                        } else {
                            System.err.println("  ✖  Enter a number between 1 and " + (users.size() + 1));
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("  ✖  Invalid input. Enter a number.");
                    }
                }
            }
        }

        // ── Step 4: POST /api/tasks ───────────────────────────────────────
        System.out.println();
        System.out.println("Creating task...");

        CreateTaskRequest request = new CreateTaskRequest();
        request.setTitle(title);
        request.setDescription(description != null && !description.isBlank() ? description : null);
        request.setAssignedTo(assignedTo);

        ApiResponse<TaskDTO> response =
                client.post("/tasks", request, new TypeReference<>() {}, true);

        // ── Step 5: Result ────────────────────────────────────────────────
        if (response.isSuccess()) {
            TaskDTO task = response.getData();
            System.out.println();
            System.out.println("  ✔  Task created successfully!");
            System.out.println("     ID    : " + task.getId());
            System.out.println("     Title : " + task.getTitle());
            System.out.println("     Status: " + task.getStatus());
            if (assignedTo != null) {
                System.out.println("     ✉  Assignee has been notified.");
            }
            System.out.println();
            return 0;

        } else {
            System.err.println();
            System.err.println("  ✖  Failed to create task: " + response.getMessage());
            System.err.println();
            return 1;
        }
    }
}
