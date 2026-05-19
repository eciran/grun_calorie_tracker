package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.UserProfileDto;
import com.grun.calorietracker.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/admin/users", "/api/v1/admin/users"})
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Users", description = "Admin-only user management operations.")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/userList")
    @Operation(
            summary = "List all users",
            description = "Returns all user profiles for admin review and management."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User profiles returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.", content = @Content)
    })
    public List<UserProfileDto> getAllUsers() {
        return userService.getAllUsers();
    }
}
