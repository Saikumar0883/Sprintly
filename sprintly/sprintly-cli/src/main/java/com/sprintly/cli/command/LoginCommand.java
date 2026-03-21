package com.sprintly.cli.command;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sprintly.auth.dto.AuthResponse;
import com.sprintly.auth.dto.LoginRequest;
import com.sprintly.cli.client.SprintlyClient;
import com.sprintly.cli.config.CliConfig;
import com.sprintly.cli.util.CliPrompt;
import com.sprintly.common.dto.ApiResponse;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(name = "login", description = "Login to Sprintly")
public class LoginCommand implements Callable<Integer> {

    @Option(names = {"-e", "--email"}, description = "User email")
    private String email;

    @Option(names = {"-p", "--password"}, description = "User password")
    private String password;

    private final SprintlyClient client = new SprintlyClient();

    @Override
    public Integer call() throws Exception {

        // ── GUARD: Already logged in? ─────────────────────────────────
        CliConfig existing = CliConfig.load();
        if (existing != null && existing.getAccessToken() != null
                && !existing.getAccessToken().isBlank()) {

            // name may be null if config was saved before name field was added
            // fall back to email, then "Unknown" as last resort
            String displayName = resolveDisplayName(existing);

            System.out.println();
            System.out.println("  ℹ  You are already logged in as: " + displayName);
            System.out.println("     To switch accounts, run: sprintly logout");
            System.out.println();
            return 0;
        }
        // ─────────────────────────────────────────────────────────────

        System.out.println("Please enter your login details.");

        if (email == null) {
            email = CliPrompt.prompt("Enter your email: ");
        }
        if (password == null) {
            password = CliPrompt.promptPassword("Enter your password: ");
        }

        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);

        ApiResponse<AuthResponse> response =
                client.post("/auth/login", request, new TypeReference<>() {}, false);

        if (response != null && response.isSuccess()) {
            AuthResponse auth = response.getData();

            // Save tokens — login response has email but not name
            // name stays null here; RegisterCommand saves the actual name
            CliConfig.save(
                    auth.getAccessToken(),
                    auth.getRefreshToken(),
                    null,
                    auth.getEmail()
            );

            System.out.println();
            System.out.println("  ✔  Login successful!");
            System.out.println("     Welcome back, " + auth.getEmail() + " 👋");
            System.out.println();
            return 0;

        } else {
            System.err.println();
            System.err.println("  ✖  Login failed: "
                    + (response != null ? response.getMessage() : "No response from server"));
            System.err.println();
            return 1;
        }
    }

    /**
     * Resolves the best display name from the config.
     * Order of preference: name → email → "Unknown user"
     *
     * Why needed:
     *   Old config files saved before the name field was added
     *   will have name = null. We must not show "null" to the user.
     */
    private String resolveDisplayName(CliConfig config) {
        if (config.getName() != null && !config.getName().isBlank()) {
            return config.getName();
        }
        if (config.getEmail() != null && !config.getEmail().isBlank()) {
            return config.getEmail();
        }
        return "Unknown user";
    }
}