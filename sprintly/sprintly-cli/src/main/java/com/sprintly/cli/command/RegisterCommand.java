package com.sprintly.cli.command;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sprintly.auth.dto.AuthResponse;
import com.sprintly.auth.dto.RegisterRequest;
import com.sprintly.cli.client.SprintlyClient;
import com.sprintly.cli.config.CliConfig;
import com.sprintly.common.dto.ApiResponse;
import com.sprintly.cli.util.CliPrompt;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(name = "register", description = "Register a new user")
public class RegisterCommand implements Callable<Integer> {

    @Option(names = {"-e", "--email"}, description = "User email")
    private String email;

    @Option(names = {"-p", "--password"}, description = "User password")
    private String password;

    @Option(names = {"-n", "--name"}, description = "User full name")
    private String name;

    private final SprintlyClient client = new SprintlyClient();

    @Override
    public Integer call() throws Exception {
        if (email == null && password == null && name == null) {
            System.out.println("Hey hi! Please enter your details.");
        }
        
        if (name == null) {
            name = CliPrompt.prompt("Enter username: ");
        }
        if (email == null) {
            email = CliPrompt.prompt("Enter your email: ");
        }
        if (password == null) {
            password = CliPrompt.promptPassword("Enter your password: ");
        }

        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setPassword(password);
        request.setName(name);

        ApiResponse<AuthResponse> response = client.post("/auth/register", request, new TypeReference<>() {}, false);

        if (response.isSuccess()) {
            AuthResponse auth = response.getData();
            CliConfig.save(auth.getAccessToken(), auth.getRefreshToken());
            System.out.println("Registration successful! Tokens saved.");
            return 0;
        } else {
            System.err.println("Registration failed: " + response.getMessage());
            return 1;
        }
    }
}
