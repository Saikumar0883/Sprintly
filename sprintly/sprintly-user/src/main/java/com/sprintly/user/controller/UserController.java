package com.sprintly.user.controller;

import com.sprintly.common.dto.ApiResponse;
import com.sprintly.common.dto.UserDTO;
import com.sprintly.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for user management.
 *
 * Currently exposes a single endpoint to list all users.
 * Used by the CLI task create command to populate the assignee selection list.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(
        name = "Users",
        description = "User management. GET /api/users is called by the CLI " +
                "'sprintly task create' command to show the assignee selection list."
)
@SecurityRequirement(name = "BearerAuth")
public class UserController {

    private final UserRepository userRepository;

    @Operation(
            summary = "List all users",
            description = "Returns all registered users with their ID, name and email. " +
                    "Used by the task creation flow to select an assignee. " +
                    "The CLI fetches this list and shows it as a numbered selection menu."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Users retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Not authenticated — provide a Bearer token")
    })
    @GetMapping
    public ApiResponse<List<UserDTO>> getAllUsers() {
        List<UserDTO> users = userRepository.findAll().stream()
                .map(u -> UserDTO.builder()
                        .id(u.getId())
                        .name(u.getName())
                        .email(u.getEmail())
                        .build())
                .collect(Collectors.toList());
        return ApiResponse.success(users);
    }
}