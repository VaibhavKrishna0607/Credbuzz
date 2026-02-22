package com.credbuzz.service;

import com.credbuzz.dto.CreateTaskRequest;
import com.credbuzz.dto.TaskDto;
import com.credbuzz.dto.UserDto;
import com.credbuzz.entity.Task;
import com.credbuzz.entity.TaskStatus;
import com.credbuzz.entity.User;
import com.credbuzz.repository.TaskRepository;
import com.credbuzz.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ============================================
 * LEARNING NOTE: Task Service
 * ============================================
 * 
 * Business logic for task operations.
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    /**
     * Get available (open) tasks
     */
    public List<TaskDto> getAvailableTasks() {
        List<Task> tasks = taskRepository.findByStatusOrderByCreatedAtDesc(TaskStatus.OPEN);
        return tasks.stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Get tasks with optional filters
     */
    public List<TaskDto> getTasks(String search, String category, String status, Long userId) {
        List<Task> tasks;
        
        if (search != null && !search.isEmpty()) {
            tasks = taskRepository.searchTasks(search);
        } else if (status != null || category != null || userId != null) {
            TaskStatus taskStatus = status != null ? TaskStatus.valueOf(status.toUpperCase()) : null;
            tasks = taskRepository.findWithFilters(taskStatus, category, userId);
        } else {
            tasks = taskRepository.findAll();
        }
        
        return tasks.stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Get single task by ID
     */
    public TaskDto getTask(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found"));
        return toDto(task);
    }

    /**
     * Get all tasks for a user (posted or assigned to them)
     */
    public List<TaskDto> getMyTasks(Long userId) {
        List<Task> tasks = taskRepository.findByUserIdPostedOrAssigned(userId);
        return tasks.stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Create a new task
     */
    @Transactional
    public TaskDto createTask(CreateTaskRequest request, Long posterId) {
        User poster = userRepository.findById(posterId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check if user has enough credits
        if (poster.getCredits() < request.getCredits()) {
            throw new RuntimeException("Not enough credits to create this task");
        }

        // Deduct credits from poster
        poster.setCredits(poster.getCredits() - request.getCredits());
        userRepository.save(poster);

        // Create task
        Task task = Task.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .category(request.getCategory())
                .credits(request.getCredits())
                .deadline(request.getDeadline())
                .skills(request.getSkills())
                .poster(poster)
                .status(TaskStatus.OPEN)
                .build();

        Task savedTask = taskRepository.save(task);
        return toDto(savedTask);
    }

    /**
     * Claim a task
     */
    @Transactional
    public TaskDto claimTask(Long taskId, Long userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Validate
        if (task.getStatus() != TaskStatus.OPEN) {
            throw new RuntimeException("Task is not available");
        }
        if (task.getPoster().getId().equals(userId)) {
            throw new RuntimeException("Cannot claim your own task");
        }

        // Claim task
        task.setAssignee(user);
        task.setStatus(TaskStatus.IN_PROGRESS);

        Task savedTask = taskRepository.save(task);
        return toDto(savedTask);
    }

    /**
     * Submit a task
     */
    @Transactional
    public TaskDto submitTask(Long taskId, Long userId, String content) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        // Validate
        if (!task.getAssignee().getId().equals(userId)) {
            throw new RuntimeException("Only assignee can submit");
        }
        if (task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw new RuntimeException("Task is not in progress");
        }

        // Submit
        task.setSubmission(content);
        task.setSubmittedAt(LocalDateTime.now());
        task.setStatus(TaskStatus.SUBMITTED);

        Task savedTask = taskRepository.save(task);
        return toDto(savedTask);
    }

    /**
     * Approve a task submission
     */
    @Transactional
    public TaskDto approveTask(Long taskId, Long userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        // Validate
        if (!task.getPoster().getId().equals(userId)) {
            throw new RuntimeException("Only task poster can approve");
        }
        if (task.getStatus() != TaskStatus.SUBMITTED) {
            throw new RuntimeException("Task is not submitted");
        }

        // Transfer credits to assignee
        User assignee = task.getAssignee();
        assignee.setCredits(assignee.getCredits() + task.getCredits());
        userRepository.save(assignee);

        // Complete task
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());

        Task savedTask = taskRepository.save(task);
        return toDto(savedTask);
    }

    /**
     * Reject a task submission
     */
    @Transactional
    public TaskDto rejectTask(Long taskId, Long userId, String reason) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        // Validate
        if (!task.getPoster().getId().equals(userId)) {
            throw new RuntimeException("Only task poster can reject");
        }
        if (task.getStatus() != TaskStatus.SUBMITTED) {
            throw new RuntimeException("Task is not submitted");
        }

        // Reset to in progress
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setSubmission(null);
        task.setSubmittedAt(null);

        Task savedTask = taskRepository.save(task);
        return toDto(savedTask);
    }

    /**
     * Cancel a task
     */
    @Transactional
    public TaskDto cancelTask(Long taskId, Long userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        // Validate - only poster can cancel
        if (!task.getPoster().getId().equals(userId)) {
            throw new RuntimeException("Only task poster can cancel");
        }

        // Refund credits to poster
        User poster = task.getPoster();
        poster.setCredits(poster.getCredits() + task.getCredits());
        userRepository.save(poster);

        // Cancel task
        task.setStatus(TaskStatus.CANCELLED);

        Task savedTask = taskRepository.save(task);
        return toDto(savedTask);
    }

    /**
     * Delete a task
     */
    @Transactional
    public void deleteTask(Long taskId, Long userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        // Validate - only poster can delete
        if (!task.getPoster().getId().equals(userId)) {
            throw new RuntimeException("Only task poster can delete");
        }

        // Only allow deleting open tasks
        if (task.getStatus() == TaskStatus.OPEN) {
            // Refund credits
            User poster = task.getPoster();
            poster.setCredits(poster.getCredits() + task.getCredits());
            userRepository.save(poster);
        }

        taskRepository.delete(task);
    }

    /**
     * Convert Task entity to DTO
     */
    private TaskDto toDto(Task task) {
        TaskDto dto = TaskDto.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .category(task.getCategory())
                .credits(task.getCredits())
                .status(task.getStatus().name())
                .skills(task.getSkills())
                .deadline(task.getDeadline())
                .submission(task.getSubmission())
                .submittedAt(task.getSubmittedAt())
                .completedAt(task.getCompletedAt())
                .createdAt(task.getCreatedAt())
                .build();

        // Add poster info
        if (task.getPoster() != null) {
            dto.setPoster(userService.toDto(task.getPoster()));
        }

        // Add assignee info
        if (task.getAssignee() != null) {
            dto.setAssignee(userService.toDto(task.getAssignee()));
        }

        return dto;
    }
}
