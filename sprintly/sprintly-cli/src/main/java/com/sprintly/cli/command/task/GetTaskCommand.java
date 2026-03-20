package com.sprintly.cli.command.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sprintly.cli.client.SprintlyClient;
import com.sprintly.common.dto.ApiResponse;
import com.sprintly.task.dto.TaskDTO;
import com.sprintly.cli.util.CliPrompt;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

@Command(name = "get", description = "Get task details")
public class GetTaskCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Task ID", arity = "0..1")
    private Long id;

    private final SprintlyClient client = new SprintlyClient();

    @Override
    public Integer call() throws Exception {
        if (id == null) {
            id = CliPrompt.promptLong("Enter task ID: ");
        }

        ApiResponse<TaskDTO> response = client.get("/tasks/" + id, new TypeReference<>() {}, true);

        if (response.isSuccess()) {
            TaskDTO task = response.getData();
            System.out.println("Task Details:");
            System.out.println("ID:          " + task.getId());
            System.out.println("Title:       " + task.getTitle());
            System.out.println("Description: " + task.getDescription());
            System.out.println("Status:      " + task.getStatus());
            System.out.println("Assigned To: " + (task.getAssignedTo() != null ? task.getAssignedTo() : "None"));
            System.out.println("Created By:  " + task.getCreatedBy());
            System.out.println("Created At:  " + task.getCreatedAt());
            System.out.println("Updated At:  " + task.getUpdatedAt());
            return 0;
        } else {
            System.err.println("Failed to get task: " + response.getMessage());
            return 1;
        }
    }
}
