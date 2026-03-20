package com.sprintly.cli.command;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sprintly.auth.dto.AuthResponse;
import com.sprintly.auth.dto.LoginRequest;
import com.sprintly.cli.client.SprintlyClient;
import com.sprintly.cli.config.CliConfig;
import com.sprintly.common.dto.ApiResponse;
import com.sprintly.cli.util.CliPrompt;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(name = "login", description = "Login to the system")
public class LoginCommand implements Callable<Integer> {

    @Option(names = {"-e", "--email"}, description = "User email")
    private String email;

    @Option(names = {"-p", "--password"}, description = "User password")
    private String password;

    private final SprintlyClient client = new SprintlyClient();

    @Override
    public Integer call() throws Exception {
        if (email == null && password == null) {
            System.out.println("Please enter your login details.");
        }
        if (email == null) {
            email = CliPrompt.prompt("Enter your email: ");
        }
        if (password == null) {
            password = CliPrompt.promptPassword("Enter your password: ");
        }

        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);

        ApiResponse<AuthResponse> response = client.post("/auth/login", request, new TypeReference<>() {}, false);

        if (response.isSuccess()) {
            AuthResponse auth = response.getData();
            CliConfig.save(auth.getAccessToken(), auth.getRefreshToken());
            System.out.println("Login successful! Tokens saved.");
            return 0;
        } else {
            System.err.println("Login failed: " + response.getMessage());
            return 1;
        }
    }
}
