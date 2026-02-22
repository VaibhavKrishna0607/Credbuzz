package com.credbuzz.controller;

import com.credbuzz.dto.*;
import com.credbuzz.entity.User;
import com.credbuzz.service.TaskService;
import com.credbuzz.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ============================================
 * LEARNING NOTE: Task Controller
 * ============================================
 * 
 * Handles all task-related HTTP endpoints.
 * Maps to your Express taskRoutes.js
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final UserService userService;

    /**
     * Get available tasks (public)
     * 
     * GET /api/tasks/available
     */
    @GetMapping("/available")
    public ResponseEntity<ApiResponse<List<TaskDto>>> getAvailableTasks() {
        try {
            List<TaskDto> tasks = taskService.getAvailableTasks();
            return ResponseEntity.ok(ApiResponse.success(tasks));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Get all tasks with filters (public)
     * 
     * GET /api/tasks?search=...&category=...&status=...&userId=...
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskDto>>> getTasks(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long userId
    ) {
        try {
            List<TaskDto> tasks = taskService.getTasks(search, category, status, userId);
            return ResponseEntity.ok(ApiResponse.success(tasks));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Get single task (public)
     * 
     * GET /api/tasks/:id
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskDto>> getTask(@PathVariable Long id) {
        try {
            TaskDto task = taskService.getTask(id);
            return ResponseEntity.ok(ApiResponse.success(task));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Create a new task (protected)
     * 
     * POST /api/tasks
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TaskDto>> createTask(
            @Valid @RequestBody CreateTaskRequest request
    ) {
        try {
            Long userId = getCurrentUserId();
            TaskDto task = taskService.createTask(request, userId);
            return ResponseEntity.ok(ApiResponse.success("Task created successfully", task));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Claim a task (protected)
     * 
     * PUT /api/tasks/:id/claim
     */
    @PutMapping("/{id}/claim")
    public ResponseEntity<ApiResponse<TaskDto>> claimTask(@PathVariable Long id) {
        try {
            Long userId = getCurrentUserId();
            TaskDto task = taskService.claimTask(id, userId);
            return ResponseEntity.ok(ApiResponse.success("Task claimed successfully", task));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Submit a task (protected)
     * 
     * PUT /api/tasks/:id/submit
     * 
     * TODO: Handle file uploads with @RequestParam("files") MultipartFile[]
     */
    @PutMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<TaskDto>> submitTask(
            @PathVariable Long id,
            @RequestBody SubmitTaskRequest request
    ) {
        try {
            Long userId = getCurrentUserId();
            TaskDto task = taskService.submitTask(id, userId, request.getContent());
            return ResponseEntity.ok(ApiResponse.success("Task submitted successfully", task));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Approve a task (protected)
     * 
     * PUT /api/tasks/:id/approve
     */
    @PutMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<TaskDto>> approveTask(@PathVariable Long id) {
        try {
            Long userId = getCurrentUserId();
            TaskDto task = taskService.approveTask(id, userId);
            return ResponseEntity.ok(ApiResponse.success("Task approved successfully", task));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Reject a task (protected)
     * 
     * PUT /api/tasks/:id/reject
     */
    @PutMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<TaskDto>> rejectTask(
            @PathVariable Long id,
            @RequestBody RejectTaskRequest request
    ) {
        try {
            Long userId = getCurrentUserId();
            TaskDto task = taskService.rejectTask(id, userId, request.getReason());
            return ResponseEntity.ok(ApiResponse.success("Task rejected", task));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Cancel a task (protected)
     * 
     * PUT /api/tasks/:id/cancel
     */
    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<TaskDto>> cancelTask(@PathVariable Long id) {
        try {
            Long userId = getCurrentUserId();
            TaskDto task = taskService.cancelTask(id, userId);
            return ResponseEntity.ok(ApiResponse.success("Task cancelled", task));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Delete a task (protected)
     * 
     * DELETE /api/tasks/:id
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteTask(@PathVariable Long id) {
        try {
            Long userId = getCurrentUserId();
            taskService.deleteTask(id, userId);
            return ResponseEntity.ok(ApiResponse.success("Task deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // ============================================
    // HELPER METHODS
    // ============================================
    
    /**
     * Get current authenticated user's ID
     * 
     * In Express: req.user.id
     * In Spring: SecurityContextHolder contains the authenticated user
     */
    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getId();
    }
}

// Request DTOs for task operations
@lombok.Data
class SubmitTaskRequest {
    private String content;
}

@lombok.Data
class RejectTaskRequest {
    private String reason;
}
