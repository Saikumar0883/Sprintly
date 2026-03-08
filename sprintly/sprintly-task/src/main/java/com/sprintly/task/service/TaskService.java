package com.sprintly.task.service;

import com.sprintly.task.dto.CreateTaskRequest;
import com.sprintly.task.dto.TaskDTO;
import com.sprintly.task.dto.UpdateTaskRequest;
import com.sprintly.task.entity.Task;
import com.sprintly.task.mapper.TaskMapper;
import com.sprintly.task.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class TaskService {

    private final TaskRepository repo;

    public TaskService(TaskRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<TaskDTO> listTasks() {
        return repo.findAll().stream()
                .map(TaskMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<TaskDTO> getTask(Long id) {
        return repo.findById(id).map(TaskMapper::toDto);
    }

    @Transactional
    public TaskDTO createTask(CreateTaskRequest req, Long creatorId) {
        Task t = Task.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .status("TODO")
                .createdBy(creatorId)
                .assignedTo(req.getAssignedTo())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        Task saved = repo.save(t);
        return TaskMapper.toDto(saved);
    }

    @Transactional
    public Optional<TaskDTO> updateTask(Long id, UpdateTaskRequest req) {
        Optional<Task> opt = repo.findById(id);
        if (opt.isEmpty()) return Optional.empty();
        Task task = opt.get();
        if (req.getTitle() != null) task.setTitle(req.getTitle());
        if (req.getDescription() != null) task.setDescription(req.getDescription());
        if (req.getStatus() != null) task.setStatus(req.getStatus());
        if (req.getAssignedTo() != null) task.setAssignedTo(req.getAssignedTo());
        task.setUpdatedAt(LocalDateTime.now());
        Task updated = repo.save(task);
        return Optional.of(TaskMapper.toDto(updated));
    }

    @Transactional
    public boolean deleteTask(Long id) {
        return repo.deleteById(id);
    }
}
