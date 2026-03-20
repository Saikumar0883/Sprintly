package com.sprintly.task.controller;

import com.sprintly.task.dto.CreateTaskRequest;
import com.sprintly.task.dto.TaskDTO;
import com.sprintly.task.dto.UpdateTaskRequest;
import com.sprintly.task.service.TaskService;
import com.sprintly.common.dto.ApiResponse;
import com.sprintly.user.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;
    private final UserRepository userRepository;

    public TaskController(TaskService taskService, UserRepository userRepository) {
        this.taskService = taskService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskDTO>>> list() {
        List<TaskDTO> tasks = taskService.listTasks();
        return ResponseEntity.ok(ApiResponse.success("Tasks retrieved successfully", tasks));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskDTO>> get(@PathVariable Long id) {
        Optional<TaskDTO> opt = taskService.getTask(id);
        return opt.map(t -> ResponseEntity.ok(ApiResponse.success("Task retrieved successfully", t)))
                  .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TaskDTO>> create(@Valid @RequestBody CreateTaskRequest req,
                                                       Principal principal) {
        // Get the current user ID from the authenticated principal (email)
        String userEmail = principal.getName();
        Long creatorId = userRepository.findByEmail(userEmail)
                .map(user -> user.getId())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

        TaskDTO dto = taskService.createTask(req, creatorId);
        return ResponseEntity.ok(ApiResponse.success("Task created successfully", dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskDTO>> update(@PathVariable Long id,
                                                       @Valid @RequestBody UpdateTaskRequest req) {
        Optional<TaskDTO> opt = taskService.updateTask(id, req);
        return opt.map(t -> ResponseEntity.ok(ApiResponse.success("Task updated successfully", t)))
                  .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        boolean deleted = taskService.deleteTask(id);
        if (deleted) return ResponseEntity.noContent().build();
        return ResponseEntity.notFound().build();
    }
}
