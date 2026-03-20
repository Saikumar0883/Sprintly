package com.sprintly.cli.command.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sprintly.cli.client.SprintlyClient;
import com.sprintly.common.dto.ApiResponse;
import com.sprintly.task.dto.CreateTaskRequest;
import com.sprintly.task.dto.TaskDTO;
import com.sprintly.cli.util.CliPrompt;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "create", description = "Create a new task")
public class CreateTaskCommand implements Callable<Integer> {

    @Option(names = {"-t", "--title"}, description = "Task title")
    private String title;

    @Option(names = {"-d", "--description"}, description = "Task description")
    private String description;

    @Option(names = {"-a", "--assignee"}, description = "Assigned user ID")
    private Long assignedTo;

    private final SprintlyClient client = new SprintlyClient();

    @Override
    public Integer call() throws Exception {
        if (title == null) {
            title = CliPrompt.prompt("Enter task title: ");
        }
        if (description == null) {
            description = CliPrompt.prompt("Enter task description: ");
        }
        if (assignedTo == null) {
            System.out.println("Fetching users...");
            ApiResponse<List<com.sprintly.common.dto.UserDTO>> usersResponse = client.get("/users", new TypeReference<>() {}, true);
            if (usersResponse.isSuccess() && usersResponse.getData() != null && !usersResponse.getData().isEmpty()) {
                List<com.sprintly.common.dto.UserDTO> users = usersResponse.getData();
                for (int i = 0; i < users.size(); i++) {
                    com.sprintly.common.dto.UserDTO u = users.get(i);
                    System.out.println((i + 1) + ". " + u.getName() + " (" + u.getEmail() + ")");
                }
                while (true) {
                    Long selection = CliPrompt.promptLong("Enter the number to assign this task: ");
                    int idx = selection.intValue() - 1;
                    if (idx >= 0 && idx < users.size()) {
                        assignedTo = users.get(idx).getId();
                        break;
                    } else {
                        System.out.println("Invalid selection. Try again.");
                    }
                }
            } else {
                System.out.println("No users found to assign.");
            }
        }

        CreateTaskRequest request = new CreateTaskRequest();
        request.setTitle(title);
        request.setDescription(description);
        request.setAssignedTo(assignedTo);

        ApiResponse<TaskDTO> response = client.post("/tasks", request, new TypeReference<>() {}, true);

        if (response.isSuccess()) {
            TaskDTO task = response.getData();
            System.out.println("Task created successfully! ID: " + task.getId());
            return 0;
        } else {
            System.err.println("Failed to create task: " + response.getMessage());
            return 1;
        }
    }
}
