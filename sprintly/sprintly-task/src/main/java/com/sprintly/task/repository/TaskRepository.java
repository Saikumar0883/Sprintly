package com.sprintly.task.repository;

import com.sprintly.task.entity.Task;

import java.util.List;
import java.util.Optional;

public interface TaskRepository {
    Optional<Task> findById(Long id);
    List<Task> findAll();
    Task save(Task task);
    boolean deleteById(Long id);
}