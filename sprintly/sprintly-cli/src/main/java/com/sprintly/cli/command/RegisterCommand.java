package com.sprintly.cli.command;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sprintly.auth.dto.AuthResponse;
import com.sprintly.auth.dto.RegisterRequest;
import com.sprintly.cli.client.SprintlyClient;
import com.sprintly.cli.config.CliConfig;
import com.sprintly.cli.util.CliPrompt;
import com.sprintly.common.dto.ApiResponse;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(name = "register", description = "Register a new Sprintly account")
public class RegisterCommand implements Callable<Integer> {

    @Option(names = {"-n", "--name"}, description = "Your full name")
    private String name;

    @Option(names = {"-e", "--email"}, description = "Your email address")
    private String email;

    @Option(names = {"-p", "--password"}, description = "Your password")
    private String password;

    private final SprintlyClient client = new SprintlyClient();

    @Override
    public Integer call() throws Exception {

        // ── GUARD: Already logged in? ─────────────────────────────────
        CliConfig existing = CliConfig.load();
        if (existing != null && existing.getAccessToken() != null
                && !existing.getAccessToken().isBlank()) {

            String displayName = (existing.getName() != null && !existing.getName().isBlank())
                    ? existing.getName()
                    : (existing.getEmail() != null ? existing.getEmail() : "Unknown user");

            System.out.println();
            System.out.println("  ℹ  You are already logged in as: " + displayName);
            System.out.println("     Run 'sprintly logout' first to create a new account.");
            System.out.println();
            return 0;
        }
        // ─────────────────────────────────────────────────────────────

        System.out.println("Hey hi! Please enter your details.");

        if (name == null) {
            name = CliPrompt.prompt("Enter your name: ");
        }
        if (email == null) {
            email = CliPrompt.prompt("Enter your email: ");
        }
        if (password == null) {
            password = CliPrompt.promptPassword("Enter your password: ");
        }

        RegisterRequest request = new RegisterRequest();
        request.setName(name);
        request.setEmail(email);
        request.setPassword(password);

        ApiResponse<AuthResponse> response =
                client.post("/auth/register", request, new TypeReference<>() {}, false);

        if (response != null && response.isSuccess()) {
            AuthResponse auth = response.getData();

            // Save with name — register is the ONLY place we have the user's
            // chosen name. Login response doesn't return name, only email.
            CliConfig.save(
                    auth.getAccessToken(),
                    auth.getRefreshToken(),
                    name,             // ← actual name entered during registration
                    auth.getEmail()
            );

            System.out.println();
            System.out.println("  ✔  Registration successful!");
            System.out.println("     Welcome to Sprintly, " + name + " 👋");
            System.out.println();
            return 0;

        } else {
            System.err.println();
            System.err.println("  ✖  Registration failed: "
                    + (response != null ? response.getMessage() : "No response from server"));
            System.err.println();
            return 1;
        }
    }
}