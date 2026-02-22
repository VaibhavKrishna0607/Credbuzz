package com.credbuzz.controller;

import com.credbuzz.dto.*;
import com.credbuzz.entity.User;
import com.credbuzz.service.TaskService;
import com.credbuzz.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ============================================
 * LEARNING NOTE: User Controller
 * ============================================
 * 
 * Handles user-related endpoints.
 * Maps to your Express userRoutes.js
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final TaskService taskService;

    /**
     * Get all users (public)
     * 
     * GET /api/users
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserDto>>> getUsers() {
        try {
            List<UserDto> users = userService.findAll().stream()
                    .map(userService::toDto)
                    .toList();
            return ResponseEntity.ok(ApiResponse.success(users));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Get user by ID (public)
     * 
     * GET /api/users/:id
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDto>> getUser(@PathVariable Long id) {
        try {
            User user = userService.findById(id)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            return ResponseEntity.ok(ApiResponse.success(userService.toDto(user)));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get current user's profile (protected)
     * 
     * GET /api/users/profile
     */
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserDto>> getProfile() {
        try {
            Long userId = getCurrentUserId();
            User user = userService.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            return ResponseEntity.ok(ApiResponse.success(userService.toDto(user)));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Update current user's profile (protected)
     * 
     * PUT /api/users/profile
     */
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserDto>> updateProfile(
            @RequestBody UpdateProfileRequest request
    ) {
        try {
            Long userId = getCurrentUserId();
            User user = userService.updateProfile(
                    userId,
                    request.getName(),
                    request.getBio(),
                    request.getSkills()
            );
            return ResponseEntity.ok(ApiResponse.success("Profile updated", userService.toDto(user)));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Get user's tasks (protected)
     * 
     * GET /api/users/my-tasks
     */
    @GetMapping("/my-tasks")
    public ResponseEntity<ApiResponse<List<TaskDto>>> getMyTasks() {
        try {
            Long userId = getCurrentUserId();
            List<TaskDto> tasks = taskService.getMyTasks(userId);
            return ResponseEntity.ok(ApiResponse.success(tasks));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Get platform statistics (public)
     * 
     * GET /api/users/user-stats
     */
    @GetMapping("/user-stats")
    public ResponseEntity<ApiResponse<PlatformStatsDto>> getUserStats() {
        try {
            // TODO: Implement platform statistics
            PlatformStatsDto stats = new PlatformStatsDto();
            // stats.setTotalUsers(userService.count());
            // stats.setTotalTasks(taskService.count());
            // stats.setCompletedTasks(taskService.countByStatus(TaskStatus.COMPLETED));
            return ResponseEntity.ok(ApiResponse.success(stats));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // ============================================
    // TODO: IMPLEMENT THESE ENDPOINTS
    // ============================================
    
    /**
     * TODO: Upload avatar
     * POST /api/users/avatar
     * 
     * @PostMapping("/avatar")
     * public ResponseEntity<ApiResponse<UserDto>> uploadAvatar(
     *     @RequestParam("file") MultipartFile file
     * ) {
     *     // 1. Validate file type (image only)
     *     // 2. Save file
     *     // 3. Update user avatar field
     *     // 4. Return updated user
     * }
     */

    /**
     * Get current authenticated user's ID
     */
    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getId();
    }
}

// Request/Response DTOs
@lombok.Data
class UpdateProfileRequest {
    private String name;
    private String bio;
    private List<String> skills;
}

@lombok.Data
class PlatformStatsDto {
    private Long totalUsers;
    private Long totalTasks;
    private Long completedTasks;
    private Long totalCreditsExchanged;
}
