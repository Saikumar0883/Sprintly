package com.sprintly.cli.command.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sprintly.cli.client.SprintlyClient;
import com.sprintly.common.dto.ApiResponse;
import com.sprintly.task.dto.TaskDTO;
import picocli.CommandLine.Command;

import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "list", description = "List all tasks")
public class ListTasksCommand implements Callable<Integer> {

    private final SprintlyClient client = new SprintlyClient();

    @Override
    public Integer call() throws Exception {
        ApiResponse<List<TaskDTO>> response = client.get("/tasks", new TypeReference<>() {}, true);

        if (response.isSuccess()) {
            List<TaskDTO> tasks = response.getData();
            if (tasks.isEmpty()) {
                System.out.println("No tasks found.");
            } else {
                System.out.printf("%-5s | %-20s | %-12s | %-10s%n", "ID", "Title", "Status", "Assignee");
                System.out.println("------------------------------------------------------------");
                for (TaskDTO task : tasks) {
                    System.out.printf("%-5d | %-20s | %-12s | %-10s%n",
                        task.getId(),
                        truncate(task.getTitle(), 20),
                        task.getStatus(),
                        task.getAssignedTo() != null ? task.getAssignedTo().toString() : "Unassigned");
                }
            }
            return 0;
        } else {
            System.err.println("Failed to list tasks: " + response.getMessage());
            return 1;
        }
    }

    private String truncate(String s, int len) {
        if (s == null) return "";
        return s.length() > len ? s.substring(0, len - 3) + "..." : s;
    }
}
