package com.sprintly.cli.command.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sprintly.cli.client.SprintlyClient;
import com.sprintly.cli.util.CliPrompt;
import com.sprintly.common.dto.ApiResponse;
import com.sprintly.task.dto.TaskDTO;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * sprintly task get <id>
 *
 * Shows ALL task information. Description is displayed in full — no "..."
 * truncation. Long descriptions word-wrap across multiple box rows.
 */
@Command(
        name = "get",
        description = "Get full details of a task by ID",
        footer = {"", "Examples:", "  task get 5", "  task get    (prompts for ID)"}
)
public class GetTaskCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Task ID", arity = "0..1")
    private Long id;

    private final SprintlyClient client = new SprintlyClient();

    // Total content width inside the box (excluding border chars)
    private static final int BOX_CONTENT = 58;
    private static final int LABEL_W     = 12;  // "Description " = 11 + 1 space
    private static final int VALUE_W     = BOX_CONTENT - LABEL_W - 2; // ": " separator

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy  HH:mm:ss");

    @Override
    public Integer call() throws Exception {

        if (!client.isLoggedIn()) {
            System.err.println();
            System.err.println("  X  Not logged in. Run: sprintly login");
            System.err.println();
            return 1;
        }

        if (id == null) {
            id = CliPrompt.promptLong("Enter task ID: ");
        }

        ApiResponse<TaskDTO> response =
                client.get("/tasks/" + id, new TypeReference<>() {}, true);

        if (response == null || !response.isSuccess()) {
            System.err.println("  X  " + (response != null
                    ? response.getMessage() : "No response from server"));
            return 1;
        }

        TaskDTO t = response.getData();

        String reporter = t.getReporterName() != null ? t.getReporterName()
                : (t.getReporterId() != null ? "#" + t.getReporterId() : "Unknown");
        String assignee = t.getAssigneeName() != null ? t.getAssigneeName() : "Unassigned";
        String created  = t.getCreatedAt() != null ? t.getCreatedAt().format(FMT) : "";
        String updated  = t.getUpdatedAt() != null ? t.getUpdatedAt().format(FMT) : "";

        System.out.println();
        top();
        row("Task ID",     String.valueOf(t.getId()));
        div();
        row("Title",       t.getTitle());
        row("Status",      t.getStatus());
        row("Reporter",    reporter);
        row("Assignee",    assignee);
        div();
        descriptionRows(t.getDescription());   // full, word-wrapped
        div();
        row("Created",     created);
        row("Updated",     updated);
        bottom();
        System.out.println();

        return 0;
    }

    // ── Box helpers ───────────────────────────────────────────────────────────

    private void top()    { System.out.println("  ┌" + "─".repeat(BOX_CONTENT + 2) + "┐"); }
    private void bottom() { System.out.println("  └" + "─".repeat(BOX_CONTENT + 2) + "┘"); }
    private void div()    { System.out.println("  ├" + "─".repeat(BOX_CONTENT + 2) + "┤"); }

    /** Single-field row with automatic word-wrap for long values. */
    private void row(String label, String value) {
        if (value == null || value.isBlank()) value = "(none)";
        List<String> lines = wrap(value, VALUE_W);
        for (int i = 0; i < lines.size(); i++) {
            if (i == 0) {
                System.out.printf("  │ %-" + LABEL_W + "s: %-" + VALUE_W + "s │%n",
                        label, lines.get(i));
            } else {
                // Continuation: indent by label width, no colon
                System.out.printf("  │ %-" + LABEL_W + "s  %-" + VALUE_W + "s │%n",
                        "", lines.get(i));
            }
        }
    }

    /**
     * Description field — shown in full with word-wrap.
     * This replaces the old truncate(description, 38) + "..." behaviour.
     *
     * Before fix:  │  Description: extract sequences for mysql and mar...│
     * After  fix:  │  Description: extract sequences for mysql and      │
     *              │               mariadb databases including stored    │
     *              │               procedures and triggers               │
     */
    private void descriptionRows(String description) {
        if (description == null || description.isBlank()) {
            System.out.printf("  │ %-" + LABEL_W + "s: %-" + VALUE_W + "s │%n",
                    "Description", "(none)");
            return;
        }

        // Preserve intentional newlines in the description
        String[] paragraphs = description.split("\n", -1);
        boolean first = true;

        for (String para : paragraphs) {
            for (String line : wrap(para.isEmpty() ? " " : para, VALUE_W)) {
                if (first) {
                    System.out.printf("  │ %-" + LABEL_W + "s: %-" + VALUE_W + "s │%n",
                            "Description", line);
                    first = false;
                } else {
                    System.out.printf("  │ %-" + LABEL_W + "s  %-" + VALUE_W + "s │%n",
                            "", line);
                }
            }
        }
    }

    /**
     * Wraps text to fit within maxWidth characters, breaking on word boundaries.
     * Hard-breaks any single word longer than maxWidth.
     */
    private List<String> wrap(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) { lines.add(""); return lines; }

        String[] words = text.split(" ");
        StringBuilder cur = new StringBuilder();

        for (String word : words) {
            // Hard-break words longer than the full width
            while (word.length() > maxWidth) {
                int space = maxWidth - cur.length();
                if (cur.length() > 0) { lines.add(cur.toString()); cur = new StringBuilder(); }
                lines.add(word.substring(0, maxWidth));
                word = word.substring(maxWidth);
            }
            if (cur.length() == 0) {
                cur.append(word);
            } else if (cur.length() + 1 + word.length() <= maxWidth) {
                cur.append(" ").append(word);
            } else {
                lines.add(cur.toString());
                cur = new StringBuilder(word);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        if (lines.isEmpty()) lines.add("");
        return lines;
    }
}