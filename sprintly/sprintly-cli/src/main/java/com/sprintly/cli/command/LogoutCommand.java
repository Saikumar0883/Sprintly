package com.sprintly.cli.command;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sprintly.cli.client.SprintlyClient;
import com.sprintly.cli.config.CliConfig;
import com.sprintly.common.dto.ApiResponse;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(name = "logout", description = "Logout from all devices")
public class LogoutCommand implements Callable<Integer> {

    private final SprintlyClient client = new SprintlyClient();

    @Override
    public Integer call() throws Exception {
        ApiResponse<Void> response = client.post("/auth/logout", null, new TypeReference<>() {}, true);

        if (response.isSuccess()) {
            CliConfig.clear();
            System.out.println("Logged out successfully.");
            return 0;
        } else {
            System.err.println("Logout failed: " + response.getMessage());
            return 1;
        }
    }
}
