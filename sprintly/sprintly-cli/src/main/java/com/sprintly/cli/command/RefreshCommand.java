package com.sprintly.cli.command;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sprintly.auth.dto.AuthResponse;
import com.sprintly.auth.dto.RefreshTokenRequest;
import com.sprintly.cli.client.SprintlyClient;
import com.sprintly.cli.config.CliConfig;
import com.sprintly.common.dto.ApiResponse;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(name = "refresh", description = "Refresh access token")
public class RefreshCommand implements Callable<Integer> {

    private final SprintlyClient client = new SprintlyClient();

    @Override
    public Integer call() throws Exception {
        CliConfig config = CliConfig.load();
        if (config == null || config.getRefreshToken() == null) {
            System.err.println("No refresh token found. Please login first.");
            return 1;
        }

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(config.getRefreshToken());

        ApiResponse<AuthResponse> response = client.post("/auth/refresh", request, new TypeReference<>() {}, false);

        if (response.isSuccess()) {
            AuthResponse auth = response.getData();
            CliConfig.save(auth.getAccessToken(), auth.getRefreshToken(), auth.getName(),auth.getEmail());
            System.out.println("Tokens refreshed successfully.");
            return 0;
        } else {
            System.err.println("Refresh failed: " + response.getMessage());
            return 1;
        }
    }
}
