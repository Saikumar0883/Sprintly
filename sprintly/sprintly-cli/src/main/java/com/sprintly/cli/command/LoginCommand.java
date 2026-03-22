package com.sprintly.cli.command;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sprintly.auth.dto.AuthResponse;
import com.sprintly.auth.dto.LoginRequest;
import com.sprintly.cli.client.SprintlyClient;
import com.sprintly.cli.config.CliConfig;
import com.sprintly.cli.command.task.BoardCommand;
import com.sprintly.cli.util.CliPrompt;
import com.sprintly.common.dto.ApiResponse;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * CLI command: sprintly login  (or /login)
 *
 * After successful login, automatically shows:
 *   1. Welcome message with name
 *   2. Unread notification count
 *   3. Kanban board view of all tasks
 *
 * This gives the user immediate context of the project state
 * right after logging in — same pattern as opening a dashboard.
 */
@Command(name = "login", description = "Login to Sprintly")
public class LoginCommand implements Callable<Integer> {

    @Option(names = {"-e", "--email"},    description = "User email")
    private String email;

    @Option(names = {"-p", "--password"}, description = "User password")
    private String password;

    private final SprintlyClient client = new SprintlyClient();

    @Override
    public Integer call() throws Exception {

        // ── Guard: already logged in ──────────────────────────────────────────
        CliConfig existing = CliConfig.load();
        if (existing != null && existing.getAccessToken() != null
                && !existing.getAccessToken().isBlank()) {
            String displayName = resolveDisplayName(existing);
            System.out.println();
            System.out.println("  i  Already logged in as: " + displayName);
            System.out.println("     To switch accounts: sprintly logout");
            System.out.println();
            return 0;
        }

        // ── Prompt for credentials ────────────────────────────────────────────
        System.out.println();
        System.out.println("  Please enter your login details.");
        System.out.println();

        if (email == null)    email    = CliPrompt.prompt("  Email   : ");
        if (password == null) password = CliPrompt.promptPassword("  Password: ");

        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);

        ApiResponse<AuthResponse> response =
                client.post("/auth/login", request, new TypeReference<>() {}, false);

        if (response != null && response.isSuccess()) {
            AuthResponse auth = response.getData();

            CliConfig.save(
                    auth.getAccessToken(),
                    auth.getRefreshToken(),
                    auth.getName(),
                    auth.getEmail()
            );

            String name = auth.getName() != null ? auth.getName() : auth.getEmail();

            // ── Success banner ────────────────────────────────────────────────
            System.out.println();
            System.out.println("  ╔═══════════════════════════════════════════════════╗");
            System.out.printf ("  ║   Welcome back, %-32s║%n", name + "! 👋");
            System.out.println("  ╚═══════════════════════════════════════════════════╝");
            System.out.println();

            // ── Show unread notification count ────────────────────────────────
            try {
                ApiResponse<Integer> unreadResp =
                        client.get("/notifications/unread/count", new TypeReference<>() {}, true);
                if (unreadResp != null && unreadResp.isSuccess()
                        && unreadResp.getData() != null && unreadResp.getData() > 0) {
                    System.out.println("  🔔  You have " + unreadResp.getData()
                            + " unread notification(s).");
                    System.out.println("      Run /notifications to view them.");
                    System.out.println();
                }
            } catch (Exception ignored) {}

            // ── Show Kanban board ─────────────────────────────────────────────
            System.out.println("  Here is your current task board:");
            System.out.println();
            new BoardCommand().call();

            System.out.println("  Type / to see all available commands.");
            System.out.println();
            return 0;

        } else {
            System.err.println();
            System.err.println("  X  Login failed: "
                    + (response != null ? response.getMessage() : "No response from server"));
            System.err.println();
            return 1;
        }
    }

    private String resolveDisplayName(CliConfig config) {
        if (config.getName()  != null && !config.getName().isBlank())  return config.getName();
        if (config.getEmail() != null && !config.getEmail().isBlank()) return config.getEmail();
        return "Unknown user";
    }
}