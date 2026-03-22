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

import java.util.concurrent.Callable;

/**
 * sprintly task update <id>
 *
 * Updates the title or description of a task.
 *
 * In interactive mode, uses CliPrompt.promptWithPrefill() so the FULL existing
 * text appears in the input buffer ready to edit. The user moves the cursor
 * with arrow keys, edits inline, and presses Enter to save.
 * Pressing Enter with no changes keeps the value unchanged.
 */
@Command(
        name = "update",
        description = "Update the title or description of a task",
        footer = {
                "",
                "Examples:",
                "  task update 5                         (existing text pre-filled, edit inline)",
                "  task update 5 --title \"New title\"",
                "  task update 5 --description \"Updated desc\"",
                "",
                "Interactive edit controls:",
                "  Arrow Left/Right  move cursor",
                "  Ctrl+A            jump to start",
                "  Ctrl+E            jump to end",
                "  Backspace/Delete  remove characters",
                "  Enter             save",
                "",
                "To change status: task status <id>"
        }
)
public class UpdateTaskCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Task ID to update", arity = "0..1")
    private Long id;

    @Option(names = {"-t", "--title"},       description = "New title")
    private String title;

    @Option(names = {"-d", "--description"}, description = "New description")
    private String description;

    private final SprintlyClient client = new SprintlyClient();

    @Override
    public Integer call() throws Exception {

        if (!client.isLoggedIn()) {
            System.out.println();
            System.out.println("  X  Not logged in. Run: sprintly login");
            System.out.println();
            return 1;
        }

        if (id == null) {
            id = CliPrompt.promptLong("Enter task ID to update: ");
        }

        ApiResponse<TaskDTO> fetchResponse =
                client.get("/tasks/" + id, new TypeReference<>() {}, true);

        if (fetchResponse == null || !fetchResponse.isSuccess()) {
            System.err.println("  X  Task not found: "
                    + (fetchResponse != null ? fetchResponse.getMessage() : "No response"));
            return 1;
        }

        TaskDTO current      = fetchResponse.getData();
        String existingTitle = current.getTitle() != null ? current.getTitle() : "";
        String existingDesc  = current.getDescription() != null ? current.getDescription() : "";

        System.out.println();
        System.out.printf("  Updating Task #%d — %s%n", current.getId(), existingTitle);
        System.out.printf("  Reporter: %s  |  Assignee: %s  |  Status: %s%n",
                current.getReporterName() != null ? current.getReporterName() : "Unknown",
                current.getAssigneeName() != null ? current.getAssigneeName() : "Unassigned",
                current.getStatus());
        System.out.println();

        String finalTitle;
        String finalDescription;

        if (title == null && description == null) {
            // ── Interactive mode ──────────────────────────────────────────────
            // The full existing text is pre-filled in the JLine buffer.
            // The user can:
            //   - Just press Enter → no change (returns the same text)
            //   - Move cursor and edit specific characters → partial edit
            //   - Select all + delete + type new text → full replace
            //
            // This solves "description is lost" — the old code showed only the
            // label and a blank input, so typing would overwrite everything.
            // Now the user sees and edits the existing text in place.

            System.out.println("  Title (edit and press Enter to save):");
            String editedTitle = CliPrompt.promptWithPrefill("  > ", existingTitle);

            if (editedTitle == null || editedTitle.isBlank()) {
                System.err.println("  X  Title cannot be empty. Keeping original.");
                finalTitle = existingTitle;
            } else {
                finalTitle = editedTitle;
            }

            System.out.println();
            System.out.println("  Description (edit and press Enter to save):");
            System.out.println("  Tip: Clear all text and press Enter to remove the description.");
            String editedDesc = CliPrompt.promptWithPrefill("  > ", existingDesc);
            finalDescription = editedDesc != null ? editedDesc : "";

        } else {
            // ── Flag mode ─────────────────────────────────────────────────────
            finalTitle       = title       != null ? title       : existingTitle;
            finalDescription = description != null ? description : existingDesc;
        }

        // Skip API if nothing changed
        boolean titleChanged = !finalTitle.equals(existingTitle);
        boolean descChanged  = !finalDescription.equals(existingDesc);

        if (!titleChanged && !descChanged) {
            System.out.println();
            System.out.println("  i  No changes made.");
            System.out.println();
            return 0;
        }

        UpdateTaskRequest request = new UpdateTaskRequest();
        request.setTitle(finalTitle);
        request.setDescription(finalDescription.isBlank() ? null : finalDescription);

        ApiResponse<TaskDTO> response =
                client.put("/tasks/" + id, request, new TypeReference<>() {}, true);

        System.out.println();
        if (response != null && response.isSuccess()) {
            TaskDTO updated = response.getData();
            System.out.println("  OK  Task updated!");
            System.out.println();
            System.out.printf("  Title      : %s%n", updated.getTitle());
            System.out.printf("  Description: %s%n",
                    updated.getDescription() != null ? updated.getDescription() : "(none)");
            System.out.printf("  Status     : %s%n", updated.getStatus());
        } else {
            System.err.println("  X  Failed: "
                    + (response != null ? response.getMessage() : "No response"));
            return 1;
        }
        System.out.println();
        return 0;
    }
}