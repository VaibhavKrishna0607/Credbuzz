package com.credbuzz.service;

import com.credbuzz.dto.BidScoreDto;
import com.credbuzz.dto.CreateTaskRequest;
import com.credbuzz.dto.TaskDto;
import com.credbuzz.dto.UserDto;
import com.credbuzz.entity.Bid;
import com.credbuzz.entity.Task;
import com.credbuzz.entity.TaskStatus;
import com.credbuzz.entity.User;
import com.credbuzz.repository.BidRepository;
import com.credbuzz.repository.TaskRepository;
import com.credbuzz.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;;

/**
 * ============================================
 * LEARNING NOTE: Task Service
 * ============================================
 * 
 * Business logic for task operations.
 * Includes auction lifecycle management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final BidRepository bidRepository;
    private final UserService userService;
    private final BidEvaluationService bidEvaluationService;
    private final UserPerformanceService userPerformanceService;
    private final EscrowService escrowService;

    /**
     * Get available (open or bidding) tasks for the marketplace
     */
    @Transactional(readOnly = true)
    public List<TaskDto> getAvailableTasks() {
        List<Task> tasks = taskRepository.findByStatusInOrderByCreatedAtDesc(
                java.util.Arrays.asList(TaskStatus.OPEN, TaskStatus.BIDDING)
        );
        return tasks.stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Get tasks with optional filters
     */
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    public TaskDto getTask(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found"));
        return toDto(task);
    }

    /**
     * Get all tasks for a user (posted or assigned to them)
     */
    @Transactional(readOnly = true)
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

    // ============================================
    // AUCTION LIFECYCLE METHODS
    // ============================================

    /**
     * Start bidding phase for a task
     * Only task creator can start bidding
     * Transition: OPEN → BIDDING
     * 
     * @param maxBids Maximum bids before auto-closing (default 5)
     */
    @Transactional
    public TaskDto startBidding(Long taskId, Long userId, LocalDateTime biddingDeadline, Integer maxBids) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        // Validation: Only task poster can start bidding
        if (!task.getPoster().getId().equals(userId)) {
            throw new RuntimeException("Only task creator can start bidding");
        }

        // Validation: Check valid transition
        if (!task.getStatus().canTransitionTo(TaskStatus.BIDDING)) {
            throw new RuntimeException("Cannot start bidding. Current status: " + task.getStatus() + 
                    ". Task must be in OPEN status.");
        }

        // Update task
        task.setStatus(TaskStatus.BIDDING);
        task.setBiddingDeadline(biddingDeadline);
        task.setMaxBids(maxBids != null ? maxBids : 5); // Default 5 bids

        Task savedTask = taskRepository.save(task);
        log.info("Started bidding for task {} with maxBids={}", taskId, task.getMaxBids());
        return toDto(savedTask);
    }

    /**
     * Close the auction/bidding phase with automatic bid selection
     * Only task creator can close bidding
     * Transition: BIDDING → AUCTION_CLOSED → ASSIGNED
     * 
     * This method:
     * 1. Validates task status is BIDDING
     * 2. Fetches and ranks all bids using BidEvaluationService
     * 3. Selects the highest scored bid
     * 4. Marks selected = true on winning bid
     * 5. Updates task status to AUCTION_CLOSED then ASSIGNED
     * 6. Assigns task to winning bidder
     * 7. Records auction history for ML training
     */
    @Transactional
    public TaskDto closeAuction(Long taskId, Long userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        // Validation: Only task poster can close auction
        if (!task.getPoster().getId().equals(userId)) {
            throw new RuntimeException("Only task creator can close the auction");
        }

        // Validation: Task must be in BIDDING or PENDING_SELECTION status
        if (task.getStatus() != TaskStatus.BIDDING && task.getStatus() != TaskStatus.PENDING_SELECTION) {
            throw new RuntimeException("Cannot close auction. Current status: " + task.getStatus() + 
                ". Task must be in BIDDING or PENDING_SELECTION status.");
        }

        // Get all bids for this task
        List<Bid> bids = bidRepository.findByTaskIdOrderByCreatedAtDesc(taskId);
        
        if (bids.isEmpty()) {
            throw new RuntimeException("Cannot close auction: No bids received");
        }

        // Evaluate and rank bids using BidEvaluationService
        List<BidScoreDto> rankedBids = bidEvaluationService.evaluateAndRankBids(task);
        log.info("Ranked {} bids for task {}", rankedBids.size(), taskId);

        // Get the best bid
        BidScoreDto bestBidScore = rankedBids.get(0);
        Bid winningBid = bidRepository.findById(bestBidScore.getBidId())
                .orElseThrow(() -> new RuntimeException("Winning bid not found"));

        log.info("Selected bid {} from user {} with score {}", 
                winningBid.getId(), winningBid.getBidder().getName(), bestBidScore.getTotalScore());

        // Mark winning bid as selected
        winningBid.setSelected(true);
        bidRepository.save(winningBid);

        // Update task status: BIDDING → AUCTION_CLOSED
        task.setStatus(TaskStatus.AUCTION_CLOSED);
        taskRepository.save(task);

        // Update task status: AUCTION_CLOSED → ASSIGNED and assign to winner
        task.setStatus(TaskStatus.ASSIGNED);
        task.setAssignee(winningBid.getBidder());
        task.setCredits(winningBid.getProposedCredits()); // Update credits to winning bid amount
        Task savedTask = taskRepository.save(task);

        // Lock credits in escrow
        escrowService.lockCredits(savedTask, winningBid.getBidder());

        // Update user performance metrics
        userPerformanceService.recordTaskAssigned(winningBid.getBidder().getId());

        log.info("Auction closed for task {}. Winner: {} (bid {})", 
                taskId, winningBid.getBidder().getName(), winningBid.getId());

        return toDto(savedTask);
    }

    /**
     * Close auction and manually select a specific bid
     * Allows task creator to override automatic selection
     */
    @Transactional
    public TaskDto closeAuctionWithBid(Long taskId, Long userId, Long selectedBidId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        // Validation: Only task poster can close auction
        if (!task.getPoster().getId().equals(userId)) {
            throw new RuntimeException("Only task creator can close the auction");
        }

        // Validation: Task must be in BIDDING, AUCTION_CLOSED, or PENDING_SELECTION status
        if (task.getStatus() != TaskStatus.BIDDING && 
            task.getStatus() != TaskStatus.AUCTION_CLOSED &&
            task.getStatus() != TaskStatus.PENDING_SELECTION) {
            throw new RuntimeException("Cannot close auction. Current status: " + task.getStatus());
        }

        // Get the selected bid
        Bid selectedBid = bidRepository.findById(selectedBidId)
                .orElseThrow(() -> new RuntimeException("Bid not found"));

        // Validate bid belongs to this task
        if (!selectedBid.getTask().getId().equals(taskId)) {
            throw new RuntimeException("Bid does not belong to this task");
        }

        // Get all bids for history
        List<Bid> bids = bidRepository.findByTaskIdOrderByCreatedAtDesc(taskId);

        // Mark selected bid
        selectedBid.setSelected(true);
        bidRepository.save(selectedBid);

        // If coming from a bidding state, transition through AUCTION_CLOSED first
        if (task.getStatus() == TaskStatus.BIDDING) {
            task.setStatus(TaskStatus.AUCTION_CLOSED);
            taskRepository.save(task);
        }
        
        task.setStatus(TaskStatus.ASSIGNED);
        task.setAssignee(selectedBid.getBidder());
        task.setCredits(selectedBid.getProposedCredits());
        Task savedTask = taskRepository.save(task);

        // Lock credits in escrow
        escrowService.lockCredits(savedTask, selectedBid.getBidder());

        // Update user performance metrics
        userPerformanceService.recordTaskAssigned(selectedBid.getBidder().getId());

        log.info("Manual selection: Task {} assigned to '{}' (from status: {})", 
                taskId, selectedBid.getBidder().getName(), task.getStatus());

        return toDto(savedTask);
    }

    /**
     * Get ranked bids for a task (preview before closing)
     */
    public List<BidScoreDto> getRankedBids(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));
        return bidEvaluationService.evaluateAndRankBids(task);
    }

    /**
     * Accept assignment (for assignee after bid is selected)
     * Transition: ASSIGNED → IN_PROGRESS
     */
    @Transactional
    public TaskDto acceptAssignment(Long taskId, Long userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        // Validation: Only assignee can accept
        if (task.getAssignee() == null || !task.getAssignee().getId().equals(userId)) {
            throw new RuntimeException("Only the assigned user can accept");
        }

        // Validation: Check valid transition
        if (!task.getStatus().canTransitionTo(TaskStatus.IN_PROGRESS)) {
            throw new RuntimeException("Cannot accept assignment. Current status: " + task.getStatus());
        }

        task.setStatus(TaskStatus.IN_PROGRESS);

        Task savedTask = taskRepository.save(task);
        return toDto(savedTask);
    }

    /**
     * Generic status transition with validation
     */
    @Transactional
    public TaskDto transitionStatus(Long taskId, Long userId, TaskStatus newStatus) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        // Validation: Check if transition is allowed
        if (!task.getStatus().canTransitionTo(newStatus)) {
            throw new RuntimeException("Invalid status transition from " + task.getStatus() + 
                    " to " + newStatus);
        }

        // Additional permission checks based on target status
        switch (newStatus) {
            case BIDDING, AUCTION_CLOSED, CANCELLED -> {
                if (!task.getPoster().getId().equals(userId)) {
                    throw new RuntimeException("Only task creator can perform this action");
                }
            }
            case IN_PROGRESS, SUBMITTED -> {
                if (task.getAssignee() == null || !task.getAssignee().getId().equals(userId)) {
                    throw new RuntimeException("Only assignee can perform this action");
                }
            }
            case COMPLETED -> {
                if (!task.getPoster().getId().equals(userId)) {
                    throw new RuntimeException("Only task creator can complete the task");
                }
            }
            default -> {}
        }

        task.setStatus(newStatus);
        Task savedTask = taskRepository.save(task);
        return toDto(savedTask);
    }

    /**
     * Claim a task (legacy - for non-auction tasks)
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
     * Allows submission from both ASSIGNED and IN_PROGRESS status
     */
    @Transactional
    public TaskDto submitTask(Long taskId, Long userId, String content) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        // Validate
        if (!task.getAssignee().getId().equals(userId)) {
            throw new RuntimeException("Only assignee can submit");
        }
        
        // Allow submission from ASSIGNED or IN_PROGRESS
        if (task.getStatus() != TaskStatus.IN_PROGRESS && task.getStatus() != TaskStatus.ASSIGNED) {
            throw new RuntimeException("Task is not in progress. Current status: " + task.getStatus());
        }

        // Submit
        task.setSubmission(content);
        task.setSubmittedAt(LocalDateTime.now());
        task.setStatus(TaskStatus.SUBMITTED);

        Task savedTask = taskRepository.save(task);
        log.info("Task {} submitted by user {}", taskId, userId);
        return toDto(savedTask);
    }

    /**
     * Approve a task submission
     * Releases escrowed credits to the assignee
     */
    @Transactional
    public TaskDto approveTask(Long taskId, Long userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        // Validate
        if (!task.getPoster().getId().equals(userId)) {
            throw new RuntimeException("Only task poster can approve");
        }
        if (task.getStatus() != TaskStatus.SUBMITTED && task.getStatus() != TaskStatus.IN_REVIEW) {
            throw new RuntimeException("Task is not submitted or in review");
        }

        // Release escrow credits to assignee
        escrowService.releaseCredits(taskId);

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
                .biddingDeadline(task.getBiddingDeadline())
                .maxBids(task.getMaxBids())
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

        // Add bid count for bidding tasks
        if (task.getStatus() == TaskStatus.BIDDING && task.getId() != null) {
            dto.setBidCount(bidRepository.countByTaskId(task.getId()));
        }

        return dto;
    }
}
